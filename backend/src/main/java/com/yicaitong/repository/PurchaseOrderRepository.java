package com.yicaitong.repository;

import com.yicaitong.domain.Domain.PurchaseOrder;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** 采购单数据访问接口，支持按创建日和实际完成日合并查询。 */
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, UUID> {
  List<PurchaseOrder>
      findByTenantIdAndCreatedAtBetweenOrTenantIdAndCompletedAtBetweenOrderByCreatedAtDesc(
          UUID firstTenantId,
          LocalDateTime firstStart,
          LocalDateTime firstEnd,
          UUID secondTenantId,
          LocalDateTime secondStart,
          LocalDateTime secondEnd);

  Optional<PurchaseOrder> findByIdAndTenantId(UUID id, UUID tenantId);

  List<PurchaseOrder> findByTenantIdAndStatusAndCreatedAtBeforeOrderByCreatedAtDesc(
      UUID tenantId, com.yicaitong.domain.Domain.OrderStatus status, LocalDateTime end);
}
