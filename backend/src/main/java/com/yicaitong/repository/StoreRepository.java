package com.yicaitong.repository;

import com.yicaitong.domain.Domain.Store;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** 按当前租户查询有效供应商店铺。 */
public interface StoreRepository extends JpaRepository<Store, UUID> {
  List<Store> findByTenantIdAndDeletedFalseOrderByLocation(UUID tenantId);

  Optional<Store> findByIdAndTenantIdAndDeletedFalse(UUID id, UUID tenantId);
}
