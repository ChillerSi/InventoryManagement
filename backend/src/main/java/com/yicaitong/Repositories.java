package com.yicaitong;

import java.time.LocalDateTime;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

interface TenantRepository extends JpaRepository<Tenant, UUID> {}

interface UserRepository extends JpaRepository<User, UUID> {
  Optional<User> findByLoginAccountIgnoreCase(String account);

  List<User> findByTenantIdOrderByOwnerDescName(UUID tenantId);
}

interface SessionRepository extends JpaRepository<Session, String> {}

interface StoreRepository extends JpaRepository<Store, UUID> {
  List<Store> findByTenantIdAndDeletedFalseOrderByLocation(UUID tenantId);

  Optional<Store> findByIdAndTenantIdAndDeletedFalse(UUID id, UUID tenantId);
}

interface ProductRepository extends JpaRepository<Product, UUID> {
  List<Product> findByTenantIdAndDeletedFalseAndOnSaleTrueOrderByIdDesc(UUID tenantId);

  List<Product> findByTenantIdAndDeletedFalseOrderByIdDesc(UUID tenantId);

  Optional<Product> findByIdAndTenantIdAndDeletedFalse(UUID id, UUID tenantId);
}

interface ProductImageRepository extends JpaRepository<ProductImage, UUID> {
  List<ProductImage> findByProductIdOrderBySortOrder(UUID productId);
}

interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, UUID> {
  List<PurchaseOrder>
      findByTenantIdAndCreatedAtBetweenOrTenantIdAndCompletedAtBetweenOrderByCreatedAtDesc(
          UUID t1, LocalDateTime s1, LocalDateTime e1, UUID t2, LocalDateTime s2, LocalDateTime e2);

  Optional<PurchaseOrder> findByIdAndTenantId(UUID id, UUID tenantId);
}
