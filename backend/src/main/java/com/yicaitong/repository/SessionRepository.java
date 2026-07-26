package com.yicaitong.repository;

import com.yicaitong.domain.Domain.Session;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionRepository extends JpaRepository<Session, String> {}
