package com.yicaitong.controller;

import com.yicaitong.domain.Domain;
import com.yicaitong.domain.Domain.Product;
import com.yicaitong.domain.Domain.Store;
import com.yicaitong.exception.ApiException;
import com.yicaitong.repository.ProductImageRepository;
import com.yicaitong.repository.ProductRepository;
import com.yicaitong.repository.StoreRepository;
import com.yicaitong.security.CurrentUser;
import com.yicaitong.security.UserContext;
import com.yicaitong.service.MediaService;
import java.math.BigDecimal;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

record StoreDto(UUID id, String name, String location, String storefrontUrl) {}

record ProductDto(
    UUID id,
    UUID storeId,
    String name,
    BigDecimal price,
    boolean onSale,
    long totalPurchasedQty,
    String storeName,
    String storeLocation,
    List<String> images) {}

record StoreInput(String name, String location) {}

record ProductInput(UUID storeId, String name, BigDecimal price, Boolean onSale) {}

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
/** 管理供应商店铺和商品档案，并负责按角色隐藏店铺名称、档口位置等敏感字段。 */
public class CatalogController {
  private final StoreRepository stores;
  private final ProductRepository products;
  private final ProductImageRepository images;
  private final MediaService media;

  /** 查询当前租户的有效店铺，仅管理员和买手可以访问。 */
  @GetMapping("/stores")
  List<StoreDto> stores() {
    UserContext.require(Domain.Role.ADMIN, Domain.Role.BUYER);
    return stores
        .findByTenantIdAndDeletedFalseOrderByLocation(UserContext.get().tenantId())
        .stream()
        .map(
            s ->
                new StoreDto(
                    s.getId(), s.getName(), s.getLocation(), media.url(s.getStorefrontObjectKey())))
        .toList();
  }

  /** 在当前租户中新建供应商店铺，仅管理员可以操作。 */
  @PostMapping("/stores")
  StoreDto createStore(@RequestBody StoreInput in) {
    UserContext.require(Domain.Role.ADMIN);
    Store s = new Store();
    s.setTenantId(UserContext.get().tenantId());
    s.setName(in.name());
    s.setLocation(in.location());
    stores.save(s);
    return new StoreDto(s.getId(), s.getName(), s.getLocation(), null);
  }

  /** 修改店铺名称和档口位置；门头图片通过独立图片接口替换。 */
  @PatchMapping("/stores/{id}")
  StoreDto updateStore(@PathVariable UUID id, @RequestBody StoreInput in) {
    UserContext.require(Domain.Role.ADMIN);
    Store store =
        stores.findByIdAndTenantIdAndDeletedFalse(id, UserContext.get().tenantId()).orElseThrow();
    if (in.name() != null && !in.name().isBlank()) store.setName(in.name());
    if (in.location() != null && !in.location().isBlank()) store.setLocation(in.location());
    stores.save(store);
    return new StoreDto(
        store.getId(),
        store.getName(),
        store.getLocation(),
        media.url(store.getStorefrontObjectKey()));
  }

  /** 软删除店铺及其关联商品，保留采购单中的历史快照。 */
  @DeleteMapping("/stores/{id}")
  void deleteStore(@PathVariable UUID id) {
    UserContext.require(Domain.Role.ADMIN);
    Store s =
        stores.findByIdAndTenantIdAndDeletedFalse(id, UserContext.get().tenantId()).orElseThrow();
    s.setDeleted(true);
    stores.save(s);
    products.findByTenantIdAndDeletedFalseOrderByIdDesc(UserContext.get().tenantId()).stream()
        .filter(p -> p.getStoreId().equals(id))
        .forEach(
            p -> {
              p.setDeleted(true);
              products.save(p);
            });
  }

  /** 按商品名、店铺名或档口位置搜索商品；无权限角色不会收到供应商敏感字段。 */
  @GetMapping("/products")
  List<ProductDto> products(
      @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "false") boolean archive) {
    CurrentUser cu = UserContext.get();
    if (archive) UserContext.require(Domain.Role.ADMIN, Domain.Role.BUYER);
    List<Product> list =
        archive
            ? products.findByTenantIdAndDeletedFalseOrderByIdDesc(cu.tenantId())
            : products.findByTenantIdAndDeletedFalseAndOnSaleTrueOrderByIdDesc(cu.tenantId());
    String query = q.trim().toLowerCase();
    return list.stream()
        .map(this::dto)
        .filter(
            p ->
                query.isBlank()
                    || (p.name()
                            + " "
                            + Objects.toString(p.storeName(), "")
                            + " "
                            + Objects.toString(p.storeLocation(), ""))
                        .toLowerCase()
                        .contains(query))
        // 选品中心和结构化搜索均优先展示历史实际采购件数更多的商品。
        .sorted(
            Comparator.comparingLong(ProductDto::totalPurchasedQty)
                .reversed()
                .thenComparing(ProductDto::name)
                .thenComparing(ProductDto::id))
        .toList();
  }

  /** 为当前租户创建商品档案，并校验目标店铺的租户归属。 */
  @PostMapping("/products")
  ProductDto create(@RequestBody ProductInput in) {
    UserContext.require(Domain.Role.ADMIN);
    Store s =
        stores
            .findByIdAndTenantIdAndDeletedFalse(in.storeId(), UserContext.get().tenantId())
            .orElseThrow();
    Product p = new Product();
    p.setTenantId(UserContext.get().tenantId());
    p.setStoreId(s.getId());
    p.setName(in.name());
    p.setPrice(in.price());
    p.setOnSale(in.onSale() == null || in.onSale());
    products.save(p);
    return dto(p);
  }

  /** 修改商品名称、价格或上下架状态，仅管理员可以操作。 */
  @PatchMapping("/products/{id}")
  ProductDto update(@PathVariable UUID id, @RequestBody ProductInput in) {
    UserContext.require(Domain.Role.ADMIN);
    Product p = owned(id);
    if (in.name() != null) p.setName(in.name());
    if (in.price() != null) p.setPrice(in.price());
    if (in.onSale() != null) p.setOnSale(in.onSale());
    products.save(p);
    return dto(p);
  }

  /** 软删除商品，使其不再出现在选品中心和以图搜图结果中。 */
  @DeleteMapping("/products/{id}")
  void delete(@PathVariable UUID id) {
    UserContext.require(Domain.Role.ADMIN);
    Product p = owned(id);
    p.setDeleted(true);
    products.save(p);
  }

  public Product owned(UUID id) {
    return products
        .findByIdAndTenantIdAndDeletedFalse(id, UserContext.get().tenantId())
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "商品不存在"));
  }

  public ProductDto dto(Product p) {
    Store s =
        stores.findByIdAndTenantIdAndDeletedFalse(p.getStoreId(), p.getTenantId()).orElse(null);
    Domain.Role role = UserContext.get().role();
    boolean storeNameVisible =
        Set.of(Domain.Role.ADMIN, Domain.Role.OPERATOR, Domain.Role.BUYER).contains(role);
    boolean storeLocationVisible = Set.of(Domain.Role.ADMIN, Domain.Role.BUYER).contains(role);
    return new ProductDto(
        p.getId(),
        p.getStoreId(),
        p.getName(),
        p.getPrice(),
        p.isOnSale(),
        p.getTotalPurchasedQty(),
        storeNameVisible && s != null ? s.getName() : null,
        storeLocationVisible && s != null ? s.getLocation() : null,
        images.findByProductIdOrderBySortOrder(p.getId()).stream()
            .map(i -> media.url(i.getObjectKey()))
            .toList());
  }
}
