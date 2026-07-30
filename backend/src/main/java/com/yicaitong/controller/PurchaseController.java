package com.yicaitong.controller;

import com.yicaitong.domain.Domain;
import com.yicaitong.domain.Domain.Product;
import com.yicaitong.domain.Domain.PurchaseOrder;
import com.yicaitong.domain.Domain.Store;
import com.yicaitong.repository.ProductImageRepository;
import com.yicaitong.repository.ProductRepository;
import com.yicaitong.repository.PurchaseOrderRepository;
import com.yicaitong.repository.StoreRepository;
import com.yicaitong.repository.UserRepository;
import com.yicaitong.security.UserContext;
import com.yicaitong.service.MediaService;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

record OrderInput(UUID productId, int planQty, boolean urgent, String operatorRemark) {}

record CompleteInput(int actualQty, BigDecimal actualPrice, String buyerRemark) {}

record OrderUpdate(Integer planQty, String operatorRemark, String buyerRemark) {}

record OrderDto(
    UUID id,
    UUID productId,
    String productName,
    BigDecimal productPrice,
    String storeName,
    String storeLocation,
    int planQty,
    boolean urgent,
    Integer actualQty,
    BigDecimal actualPrice,
    String operatorRemark,
    String buyerRemark,
    String creatorName,
    String buyerName,
    List<String> images,
    Domain.OrderStatus status,
    LocalDateTime createdAt,
    LocalDateTime completedAt) {}

@RestController
@RequestMapping("/api/purchase-orders")
@RequiredArgsConstructor
/** 提供采购任务创建、跨天查询、修改、完成和删除接口，并执行对应角色权限。 */
public class PurchaseController {
  private final PurchaseOrderRepository orders;
  private final ProductRepository products;
  private final StoreRepository stores;
  private final UserRepository users;
  private final ProductImageRepository images;
  private final MediaService media;

  /** 合并指定日期创建及指定日期完成的采购单，用订单 ID 去重后返回。 */
  @GetMapping
  List<OrderDto> list(@RequestParam(required = false) LocalDate date) {
    LocalDate d = date == null ? LocalDate.now() : date;
    LocalDateTime s = d.atStartOfDay(), e = d.plusDays(1).atStartOfDay();
    List<PurchaseOrder> selected =
        new ArrayList<>(
            orders
                .findByTenantIdAndCreatedAtBetweenOrTenantIdAndCompletedAtBetweenOrderByCreatedAtDesc(
                    UserContext.get().tenantId(), s, e, UserContext.get().tenantId(), s, e));
    if (d.equals(LocalDate.now())) {
      selected.addAll(
          orders.findByTenantIdAndStatusAndCreatedAtBeforeOrderByCreatedAtDesc(
              UserContext.get().tenantId(), Domain.OrderStatus.PENDING, s));
    }
    return selected.stream()
        .collect(java.util.stream.Collectors.toMap(PurchaseOrder::getId, o -> o, (a, b) -> a))
        .values()
        .stream()
        .sorted(Comparator.comparing(PurchaseOrder::getCreatedAt).reversed())
        .map(this::dto)
        .toList();
  }

  /** 创建待采购任务，同时保存商品、店铺和档口信息快照。 */
  @PostMapping
  OrderDto create(@RequestBody OrderInput in) {
    UserContext.require(Domain.Role.ADMIN, Domain.Role.OPERATOR);
    Product p =
        products
            .findByIdAndTenantIdAndDeletedFalse(in.productId(), UserContext.get().tenantId())
            .orElseThrow();
    Store s =
        stores
            .findByIdAndTenantIdAndDeletedFalse(p.getStoreId(), UserContext.get().tenantId())
            .orElseThrow();
    PurchaseOrder o = new PurchaseOrder();
    o.setTenantId(UserContext.get().tenantId());
    o.setProductId(p.getId());
    o.setStoreId(s.getId());
    o.setCreatorUserId(UserContext.get().userId());
    o.setPlanQty(in.planQty());
    o.setUrgent(in.urgent());
    o.setOperatorRemark(in.operatorRemark());
    o.setProductNameSnapshot(p.getName());
    o.setStoreNameSnapshot(s.getName());
    o.setStoreLocationSnapshot(s.getLocation());
    orders.save(o);
    return dto(o);
  }

  /** 修改未完成采购单的计划数量和运营备注。 */
  @PatchMapping("/{id}")
  OrderDto edit(@PathVariable UUID id, @RequestBody OrderUpdate in) {
    PurchaseOrder o = owned(id);
    if (o.getStatus() != Domain.OrderStatus.PENDING) throw new IllegalStateException("订单已完成");
    Domain.Role role = UserContext.get().role();
    if (role == Domain.Role.ADMIN || role == Domain.Role.OPERATOR) {
      if (in.planQty() != null && in.planQty() > 0) o.setPlanQty(in.planQty());
      if (in.operatorRemark() != null) o.setOperatorRemark(in.operatorRemark());
    }
    if (role == Domain.Role.ADMIN || role == Domain.Role.BUYER) {
      if (in.buyerRemark() != null) o.setBuyerRemark(in.buyerRemark());
    }
    if (role == Domain.Role.VIEWER) {
      throw new IllegalStateException("查看者不能修改采购单");
    }
    orders.save(o);
    return dto(o);
  }

  /** 由管理员或买手填写实采数量、实采价格并完成采购。 */
  @PostMapping("/{id}/complete")
  @Transactional
  OrderDto complete(@PathVariable UUID id, @RequestBody CompleteInput in) {
    UserContext.require(Domain.Role.ADMIN, Domain.Role.BUYER);
    PurchaseOrder o = owned(id);
    o.setActualQty(in.actualQty());
    o.setActualPrice(in.actualPrice());
    o.setBuyerRemark(in.buyerRemark());
    o.setBuyerUserId(UserContext.get().userId());
    o.setCompletedAt(LocalDateTime.now());
    o.setStatus(Domain.OrderStatus.COMPLETED);
    orders.save(o);
    products
        .findByIdAndTenantIdAndDeletedFalse(o.getProductId(), UserContext.get().tenantId())
        .ifPresent(
            product -> {
              product.setTotalPurchasedQty(product.getTotalPurchasedQty() + in.actualQty());
              products.save(product);
            });
    return dto(o);
  }

  /** 删除尚未完成的采购任务；已完成记录不允许直接删除。 */
  @DeleteMapping("/{id}")
  void delete(@PathVariable UUID id) {
    UserContext.require(Domain.Role.ADMIN, Domain.Role.OPERATOR);
    PurchaseOrder o = owned(id);
    if (o.getStatus() != Domain.OrderStatus.PENDING) throw new IllegalStateException("订单已完成");
    orders.delete(o);
  }

  PurchaseOrder owned(UUID id) {
    return orders.findByIdAndTenantId(id, UserContext.get().tenantId()).orElseThrow();
  }

  OrderDto dto(PurchaseOrder o) {
    Domain.Role role = UserContext.get().role();
    boolean storeNameVisible =
        Set.of(Domain.Role.ADMIN, Domain.Role.OPERATOR, Domain.Role.BUYER).contains(role);
    boolean storeLocationVisible = Set.of(Domain.Role.ADMIN, Domain.Role.BUYER).contains(role);
    return new OrderDto(
        o.getId(),
        o.getProductId(),
        o.getProductNameSnapshot(),
        products.findById(o.getProductId()).map(Product::getPrice).orElse(BigDecimal.ZERO),
        storeNameVisible ? o.getStoreNameSnapshot() : null,
        storeLocationVisible ? o.getStoreLocationSnapshot() : null,
        o.getPlanQty(),
        o.isUrgent(),
        o.getActualQty(),
        o.getActualPrice(),
        o.getOperatorRemark(),
        o.getBuyerRemark(),
        users.findById(o.getCreatorUserId()).map(Domain.User::getName).orElse("未知运营"),
        o.getBuyerUserId() == null
            ? null
            : users.findById(o.getBuyerUserId()).map(Domain.User::getName).orElse("未知买手"),
        images.findByProductIdOrderBySortOrder(o.getProductId()).stream()
            .map(image -> media.productImageUrl(image.getId(), image.getTenantId()))
            .toList(),
        o.getStatus(),
        o.getCreatedAt(),
        o.getCompletedAt());
  }
}
