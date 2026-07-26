package com.yicaitong;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

record OrderInput(UUID productId,int planQty,String operatorRemark){}
record CompleteInput(int actualQty,BigDecimal actualPrice,String buyerRemark){}
record OrderDto(UUID id,UUID productId,String productName,String storeName,String storeLocation,int planQty,Integer actualQty,BigDecimal actualPrice,String operatorRemark,String buyerRemark,Domain.OrderStatus status,LocalDateTime createdAt,LocalDateTime completedAt){}

@RestController @RequestMapping("/api/purchase-orders") @RequiredArgsConstructor
class PurchaseController {
  private final PurchaseOrderRepository orders; private final ProductRepository products; private final StoreRepository stores;
  @GetMapping List<OrderDto> list(@RequestParam(required=false) LocalDate date) {
    LocalDate d=date==null?LocalDate.now():date; LocalDateTime s=d.atStartOfDay(),e=d.plusDays(1).atStartOfDay();
    return orders.findByTenantIdAndCreatedAtBetweenOrTenantIdAndCompletedAtBetweenOrderByCreatedAtDesc(UserContext.get().tenantId(),s,e,UserContext.get().tenantId(),s,e).stream().distinct().map(this::dto).toList();
  }
  @PostMapping OrderDto create(@RequestBody OrderInput in) {
    UserContext.require(Domain.Role.ADMIN,Domain.Role.OPERATOR); Product p=products.findByIdAndTenantIdAndDeletedFalse(in.productId(),UserContext.get().tenantId()).orElseThrow();
    Store s=stores.findByIdAndTenantIdAndDeletedFalse(p.getStoreId(),UserContext.get().tenantId()).orElseThrow(); PurchaseOrder o=new PurchaseOrder();
    o.setTenantId(UserContext.get().tenantId());o.setProductId(p.getId());o.setStoreId(s.getId());o.setCreatorUserId(UserContext.get().userId());o.setPlanQty(in.planQty());o.setOperatorRemark(in.operatorRemark());
    o.setProductNameSnapshot(p.getName());o.setStoreNameSnapshot(s.getName());o.setStoreLocationSnapshot(s.getLocation());orders.save(o);return dto(o);
  }
  @PatchMapping("/{id}") OrderDto edit(@PathVariable UUID id,@RequestBody OrderInput in) {
    UserContext.require(Domain.Role.ADMIN,Domain.Role.OPERATOR); PurchaseOrder o=owned(id); if(o.getStatus()!=Domain.OrderStatus.PENDING)throw new IllegalStateException("订单已完成");
    if(in.planQty()>0)o.setPlanQty(in.planQty());if(in.operatorRemark()!=null)o.setOperatorRemark(in.operatorRemark());orders.save(o);return dto(o);
  }
  @PostMapping("/{id}/complete") OrderDto complete(@PathVariable UUID id,@RequestBody CompleteInput in) {
    UserContext.require(Domain.Role.ADMIN,Domain.Role.BUYER);PurchaseOrder o=owned(id);o.setActualQty(in.actualQty());o.setActualPrice(in.actualPrice());o.setBuyerRemark(in.buyerRemark());
    o.setBuyerUserId(UserContext.get().userId());o.setCompletedAt(LocalDateTime.now());o.setStatus(Domain.OrderStatus.COMPLETED);orders.save(o);return dto(o);
  }
  @DeleteMapping("/{id}") void delete(@PathVariable UUID id){UserContext.require(Domain.Role.ADMIN,Domain.Role.OPERATOR);PurchaseOrder o=owned(id);if(o.getStatus()!=Domain.OrderStatus.PENDING)throw new IllegalStateException("订单已完成");orders.delete(o);}
  PurchaseOrder owned(UUID id){return orders.findByIdAndTenantId(id,UserContext.get().tenantId()).orElseThrow();}
  OrderDto dto(PurchaseOrder o){boolean visible=Set.of(Domain.Role.ADMIN,Domain.Role.BUYER).contains(UserContext.get().role());return new OrderDto(o.getId(),o.getProductId(),o.getProductNameSnapshot(),visible?o.getStoreNameSnapshot():null,visible?o.getStoreLocationSnapshot():null,o.getPlanQty(),o.getActualQty(),o.getActualPrice(),o.getOperatorRemark(),o.getBuyerRemark(),o.getStatus(),o.getCreatedAt(),o.getCompletedAt());}
}
