package com.yicaitong.controller;

import com.yicaitong.domain.Domain.ProductImage;
import com.yicaitong.domain.Domain.Store;
import com.yicaitong.exception.ApiException;
import com.yicaitong.repository.ProductImageRepository;
import com.yicaitong.repository.ProductRepository;
import com.yicaitong.repository.StoreRepository;
import com.yicaitong.service.MediaService;
import com.yicaitong.service.MediaService.MediaObject;
import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api/media")
@RequiredArgsConstructor
/** 校验短期媒体签名，并将本地 MinIO 私有图片以二进制流返回给外部浏览器。 */
public class MediaController {
  private final MediaService media;
  private final ProductImageRepository images;
  private final ProductRepository products;
  private final StoreRepository stores;

  /** 读取有效商品的原始图片；商品软删除后旧签名立即失效。 */
  @GetMapping("/product-images/{imageId}")
  ResponseEntity<StreamingResponseBody> productImage(
      @PathVariable UUID imageId,
      @RequestParam UUID tenant,
      @RequestParam long expires,
      @RequestParam String signature)
      throws Exception {
    requireSignature("product-images", imageId, tenant, expires, signature);
    ProductImage image =
        images
            .findByIdAndTenantId(imageId, tenant)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "图片不存在"));
    products
        .findByIdAndTenantIdAndDeletedFalse(image.getProductId(), tenant)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "商品不存在"));
    return stream(image.getObjectKey());
  }

  /** 读取有效店铺的门头图片；店铺软删除后旧签名立即失效。 */
  @GetMapping("/storefronts/{storeId}")
  ResponseEntity<StreamingResponseBody> storefront(
      @PathVariable UUID storeId,
      @RequestParam UUID tenant,
      @RequestParam long expires,
      @RequestParam String signature)
      throws Exception {
    requireSignature("storefronts", storeId, tenant, expires, signature);
    Store store =
        stores
            .findByIdAndTenantIdAndDeletedFalse(storeId, tenant)
            .filter(value -> value.getStorefrontObjectKey() != null)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "店铺门头图片不存在"));
    return stream(store.getStorefrontObjectKey());
  }

  private void requireSignature(
      String resourceType, UUID resourceId, UUID tenantId, long expires, String signature) {
    if (!media.validSignature(resourceType, resourceId, tenantId, expires, signature)) {
      throw new ApiException(HttpStatus.FORBIDDEN, "图片访问地址无效或已过期");
    }
  }

  private ResponseEntity<StreamingResponseBody> stream(String objectKey) throws Exception {
    MediaObject object = media.open(objectKey);
    StreamingResponseBody body =
        output -> {
          try (var input = object.stream()) {
            input.transferTo(output);
          }
        };
    MediaType contentType;
    try {
      contentType = MediaType.parseMediaType(object.contentType());
    } catch (Exception exception) {
      contentType = MediaType.APPLICATION_OCTET_STREAM;
    }
    return ResponseEntity.ok()
        .contentType(contentType)
        .contentLength(object.contentLength())
        .cacheControl(CacheControl.maxAge(Duration.ofMinutes(55)).cachePrivate())
        .header(HttpHeaders.ETAG, "\"" + object.etag() + "\"")
        .lastModified(object.lastModified().toInstant())
        .body(body);
  }
}
