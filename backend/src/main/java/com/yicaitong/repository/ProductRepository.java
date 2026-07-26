package com.yicaitong.repository;

import com.yicaitong.domain.Domain.Product;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** 商品列表及租户归属校验查询接口。 */
public interface ProductRepository extends JpaRepository<Product, UUID> {
  List<Product> findByTenantIdAndDeletedFalseAndOnSaleTrueOrderByIdDesc(UUID tenantId);

  List<Product> findByTenantIdAndDeletedFalseOrderByIdDesc(UUID tenantId);

  Optional<Product> findByIdAndTenantIdAndDeletedFalse(UUID id, UUID tenantId);
}
