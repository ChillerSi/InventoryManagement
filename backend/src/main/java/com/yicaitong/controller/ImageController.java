package com.yicaitong.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.*;
import javax.imageio.ImageIO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/images")
@RequiredArgsConstructor
@Slf4j
/** 处理商品图片上传、SigLIP 向量化和 Qdrant 相似图片检索。 */
public class ImageController {
  private final CatalogController catalog;
  private final ProductImageRepository images;
  private final ProductRepository products;
  private final StoreRepository stores;
  private final MediaService media;
  private final VectorService vectors;
  private final ObjectMapper objectMapper;

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

  /**
   * 接收一张原图和多个原图像素坐标，由服务端逐框裁剪、保存和向量化。
   *
   * <p>坐标原点位于原图左上角，最多允许 20 个框。原图只在 MinIO 和 MySQL 保存一次；裁剪区域仅在内存中用于 SigLIP 建模，不写入 MinIO。每个区域生成独立
   * Qdrant point，payload 只保存租户和商品主键；店铺与位置从 MySQL 关联查询。
   */
  @PostMapping("/products/{productId}/regions")
  ProductDto uploadRegions(
      @PathVariable UUID productId, @RequestPart MultipartFile file, @RequestPart String regions)
      throws Exception {
    UserContext.require(Domain.Role.ADMIN);
    Product product = catalog.owned(productId);
    validate(file);
    List<ImageRegion> requested =
        objectMapper.readValue(regions, new TypeReference<List<ImageRegion>>() {});
    if (requested.isEmpty() || requested.size() > 20) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "每次必须框选 1 到 20 个商品区域");
    }

    BufferedImage original = ImageIO.read(new ByteArrayInputStream(file.getBytes()));
    if (original == null) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "无法解析上传的图片");
    }
    requested.forEach(region -> validateRegion(region, original));

    ProductImage sourceImage = new ProductImage();
    sourceImage.setTenantId(UserContext.get().tenantId());
    sourceImage.setProductId(productId);
    sourceImage.setObjectKey(
        "tenant/"
            + sourceImage.getTenantId()
            + "/product/"
            + productId
            + "/source/"
            + UUID.randomUUID()
            + extension(file.getContentType()));
    media.put(file, sourceImage.getObjectKey());
    images.save(sourceImage);

    int readyCount = 0;
    String modelVersion = null;
    for (ImageRegion region : requested) {
      BufferedImage sourceCrop =
          original.getSubimage(region.x(), region.y(), region.width(), region.height());
      BufferedImage crop =
          new BufferedImage(
              sourceCrop.getWidth(), sourceCrop.getHeight(), BufferedImage.TYPE_INT_RGB);
      Graphics2D graphics = crop.createGraphics();
      graphics.drawImage(sourceCrop, 0, 0, null);
      graphics.dispose();
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      ImageIO.write(crop, "jpg", output);
      try {
        EmbeddingResult embedding = vectors.embed(output.toByteArray(), "image/jpeg");
        vectors.index(UUID.randomUUID(), sourceImage.getTenantId(), productId, embedding);
        readyCount++;
        modelVersion = embedding.modelVersion();
      } catch (Exception exception) {
        log.warn(
            "商品框选区域向量化失败: tenantId={}, storeId={}, productId={}, sourceImageId={}, region={}",
            sourceImage.getTenantId(),
            product.getStoreId(),
            productId,
            sourceImage.getId(),
            region,
            exception);
      }
    }
    sourceImage.setVectorStatus(readyCount == requested.size() ? "READY" : "FAILED");
    sourceImage.setModelVersion(modelVersion);
    images.save(sourceImage);
    return catalog.dto(product);
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
              return new AbstractMap.SimpleEntry<>(map.get(id), h.get("similarity"));
            })
        .filter(entry -> entry.getKey() != null)
        .map(
            entry ->
                Map.<String, Object>of("product", entry.getKey(), "similarity", entry.getValue()))
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

  private void validateRegion(ImageRegion region, BufferedImage original) {
    boolean invalid =
        region.x() < 0
            || region.y() < 0
            || region.width() < 8
            || region.height() < 8
            || region.x() + region.width() > original.getWidth()
            || region.y() + region.height() > original.getHeight();
    if (invalid) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "框选坐标超出原图范围或区域过小");
    }
  }

  /** 原图像素坐标，不使用浏览器缩放后的 Canvas 坐标。 */
  public record ImageRegion(int x, int y, int width, int height) {}
}
