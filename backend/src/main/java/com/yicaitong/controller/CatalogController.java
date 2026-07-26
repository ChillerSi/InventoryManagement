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
public class CatalogController {
  private final StoreRepository stores;
  private final ProductRepository products;
  private final ProductImageRepository images;
  private final MediaService media;

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
        .toList();
  }

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
    boolean visible =
        Set.of(Domain.Role.ADMIN, Domain.Role.BUYER).contains(UserContext.get().role());
    return new ProductDto(
        p.getId(),
        p.getStoreId(),
        p.getName(),
        p.getPrice(),
        p.isOnSale(),
        p.getTotalPurchasedQty(),
        visible && s != null ? s.getName() : null,
        visible && s != null ? s.getLocation() : null,
        images.findByProductIdOrderBySortOrder(p.getId()).stream()
            .map(i -> media.url(i.getObjectKey()))
            .toList());
  }
}
