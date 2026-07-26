package com.yicaitong.repository;

import com.yicaitong.domain.Domain.Session;
import org.springframework.data.jpa.repository.JpaRepository;

/** 服务端登录会话数据访问接口。 */
public interface SessionRepository extends JpaRepository<Session, String> {}
