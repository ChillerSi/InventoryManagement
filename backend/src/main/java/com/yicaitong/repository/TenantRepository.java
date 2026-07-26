package com.yicaitong.repository;

import com.yicaitong.domain.Domain.Tenant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** 租户基础数据访问接口。 */
public interface TenantRepository extends JpaRepository<Tenant, UUID> {}
