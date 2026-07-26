package com.yicaitong.repository;

import com.yicaitong.domain.Domain.ProductImage;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductImageRepository extends JpaRepository<ProductImage, UUID> {
  List<ProductImage> findByProductIdOrderBySortOrder(UUID productId);
}
