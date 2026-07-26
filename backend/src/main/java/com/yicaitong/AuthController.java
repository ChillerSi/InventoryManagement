package com.yicaitong;

import jakarta.transaction.Transactional;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
class AuthController {
  private final TenantRepository tenants;
  private final UserRepository users;
  private final SessionRepository sessions;

  record Register(
      @NotBlank String company,
      @NotBlank String name,
      @NotBlank String account,
      @Size(min = 6) String password) {}

  record Login(@NotBlank String account, @NotBlank String password) {}

  record AuthView(String token, UUID userId, String name, String company, Domain.Role role) {}

  @PostMapping("/register")
  @Transactional
  AuthView register(@RequestBody Register r) {
    users
        .findByLoginAccountIgnoreCase(r.account())
        .ifPresent(
            x -> {
              throw new ApiException(HttpStatus.CONFLICT, "登录账号已存在");
            });
    Tenant t = new Tenant();
    t.setName(r.company());
    tenants.save(t);
    User u = new User();
    u.setTenantId(t.getId());
    u.setName(r.name());
    u.setLoginAccount(r.account().trim().toLowerCase());
    u.setPassword(r.password());
    u.setRole(Domain.Role.ADMIN);
    u.setOwner(true);
    users.save(u);
    return issue(u, t.getName());
  }

  @PostMapping("/login")
  AuthView login(@RequestBody Login r) {
    User u =
        users
            .findByLoginAccountIgnoreCase(r.account())
            .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "账号或密码错误"));
    if (!u.isActive() || !u.getPassword().equals(r.password()))
      throw new ApiException(HttpStatus.UNAUTHORIZED, "账号或密码错误");
    return issue(u, tenants.findById(u.getTenantId()).orElseThrow().getName());
  }

  private AuthView issue(User u, String company) {
    Session s = new Session();
    s.setToken(UUID.randomUUID().toString());
    s.setUserId(u.getId());
    s.setTenantId(u.getTenantId());
    s.setRole(u.getRole());
    s.setExpiresAt(LocalDateTime.now().plusDays(7));
    sessions.save(s);
    return new AuthView(s.getToken(), u.getId(), u.getName(), company, u.getRole());
  }
}
