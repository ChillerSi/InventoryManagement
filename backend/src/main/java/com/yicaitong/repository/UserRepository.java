package com.yicaitong.repository;

import com.yicaitong.domain.Domain.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** 用户账号查询接口，包含全局账号登录和租户团队列表查询。 */
public interface UserRepository extends JpaRepository<User, UUID> {
  Optional<User> findByLoginAccountIgnoreCase(String account);

  List<User> findByTenantIdOrderByOwnerDescName(UUID tenantId);
}
