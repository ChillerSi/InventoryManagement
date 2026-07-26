package com.yicaitong.repository;

import com.yicaitong.domain.Domain.PurchaseOrder;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
