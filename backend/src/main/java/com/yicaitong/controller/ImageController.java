package com.yicaitong.controller;

import com.yicaitong.domain.Domain;
import com.yicaitong.domain.Domain.Product;
import com.yicaitong.domain.Domain.ProductImage;
import com.yicaitong.repository.ProductImageRepository;
import com.yicaitong.repository.ProductRepository;
import com.yicaitong.security.UserContext;
import com.yicaitong.service.MediaService;
import com.yicaitong.service.VectorService;
import java.util.*;
import lombok.RequiredArgsConstructor;
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
  private final MediaService media;
  private final VectorService vectors;

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
      List<Double> v = vectors.embed(file.getBytes(), file.getContentType());
      vectors.index(pi.getId(), pi.getTenantId(), productId, v);
      pi.setVectorStatus("READY");
      pi.setModelVersion("siglip");
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
}
