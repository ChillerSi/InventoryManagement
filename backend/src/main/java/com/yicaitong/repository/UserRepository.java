package com.yicaitong.repository;

import com.yicaitong.domain.Domain.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {
  Optional<User> findByLoginAccountIgnoreCase(String account);

  List<User> findByTenantIdOrderByOwnerDescName(UUID tenantId);
}
