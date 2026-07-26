package com.yicaitong.controller;

import com.yicaitong.domain.Domain;
import com.yicaitong.domain.Domain.Product;
import com.yicaitong.domain.Domain.ProductImage;
import com.yicaitong.domain.Domain.Store;
import com.yicaitong.exception.ApiException;
import com.yicaitong.repository.ProductImageRepository;
import com.yicaitong.repository.ProductRepository;
import com.yicaitong.repository.StoreRepository;
import com.yicaitong.security.UserContext;
import com.yicaitong.service.MediaService;
import com.yicaitong.service.VectorService;
import com.yicaitong.service.VectorService.EmbeddingResult;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/images")
@RequiredArgsConstructor
/** 处理商品图片上传、SigLIP 向量化和 Qdrant 相似图片检索。 */
public class ImageController {
  private final CatalogController catalog;
  private final ProductImageRepository images;
  private final ProductRepository products;
  private final StoreRepository stores;
  private final MediaService media;
  private final VectorService vectors;

  /** 上传并替换店铺唯一门头图片。该图片只保存到 MinIO，不进入 SigLIP 和 Qdrant。 数据库仅保存对象键，替换成功后异步语义地清理旧对象。 */
  @PostMapping("/stores/{storeId}/storefront")
  StoreDto uploadStorefront(@PathVariable UUID storeId, @RequestPart MultipartFile file)
      throws Exception {
    UserContext.require(Domain.Role.ADMIN);
    validate(file);
    Store store =
        stores
            .findByIdAndTenantIdAndDeletedFalse(storeId, UserContext.get().tenantId())
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "店铺不存在"));
    String oldObjectKey = store.getStorefrontObjectKey();
    String objectKey =
        "tenant/"
            + store.getTenantId()
            + "/store/"
            + storeId
            + "/storefront/"
            + UUID.randomUUID()
            + extension(file.getContentType());
    media.put(file, objectKey);
    store.setStorefrontObjectKey(objectKey);
    stores.save(store);
    media.deleteQuietly(oldObjectKey);
    return new StoreDto(store.getId(), store.getName(), store.getLocation(), media.url(objectKey));
  }

  /** 保存用户框选后的商品图片到 MinIO，并同步创建 SigLIP 向量。 向量失败不会回滚图片，状态会标记为 FAILED，便于后续补偿。 */
  @PostMapping("/products/{productId}")
  ProductDto upload(@PathVariable UUID productId, @RequestPart MultipartFile file)
      throws Exception {
    UserContext.require(Domain.Role.ADMIN);
    Product p = catalog.owned(productId);
    validate(file);
    ProductImage pi = new ProductImage();
    pi.setTenantId(UserContext.get().tenantId());
    pi.setProductId(productId);
    pi.setObjectKey(
        "tenant/" + pi.getTenantId() + "/product/" + productId + "/" + UUID.randomUUID() + ".jpg");
    media.put(file, pi.getObjectKey());
    images.save(pi);
    try {
      EmbeddingResult embedding = vectors.embed(file.getBytes(), file.getContentType());
      vectors.index(pi.getId(), pi.getTenantId(), productId, embedding);
      pi.setVectorStatus("READY");
      pi.setModelVersion(embedding.modelVersion());
    } catch (Exception e) {
      pi.setVectorStatus("FAILED");
    }
    images.save(pi);
    return catalog.dto(p);
  }

  /** 对框选后的查询图片建模，在当前租户和上架商品范围内返回相似度最高的 20 个商品。 */
  @PostMapping("/search")
  List<Map<String, Object>> search(
      @RequestPart MultipartFile file, @RequestParam(defaultValue = "20") int top)
      throws Exception {
    validate(file);
    List<Map<String, Object>> hits =
        vectors.search(
            UserContext.get().tenantId(),
            vectors.embed(file.getBytes(), file.getContentType()),
            Math.min(top, 20));
    Map<UUID, ProductDto> map = new HashMap<>();
    products
        .findByTenantIdAndDeletedFalseAndOnSaleTrueOrderByIdDesc(UserContext.get().tenantId())
        .forEach(p -> map.put(p.getId(), catalog.dto(p)));
    return hits.stream()
        .map(
            h -> {
              UUID id = UUID.fromString((String) h.get("productId"));
              return Map.<String, Object>of(
                  "product", map.get(id), "similarity", h.get("similarity"));
            })
        .filter(x -> x.get("product") != null)
        .toList();
  }

  void validate(MultipartFile f) {
    if (f.isEmpty() || f.getContentType() == null || !f.getContentType().startsWith("image/"))
      throw new IllegalArgumentException("只允许上传图片");
  }

  private String extension(String contentType) {
    return switch (contentType) {
      case "image/png" -> ".png";
      case "image/webp" -> ".webp";
      default -> ".jpg";
    };
  }
}
