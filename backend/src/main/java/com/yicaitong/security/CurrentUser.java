package com.yicaitong.security;

import com.yicaitong.domain.Domain.Role;
import java.util.UUID;

public record CurrentUser(UUID userId, UUID tenantId, Role role) {}
