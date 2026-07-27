package com.yicaitong.controller;

import com.yicaitong.domain.Domain;
import com.yicaitong.domain.Domain.Session;
import com.yicaitong.domain.Domain.Tenant;
import com.yicaitong.domain.Domain.User;
import com.yicaitong.exception.ApiException;
import com.yicaitong.repository.SessionRepository;
import com.yicaitong.repository.TenantRepository;
import com.yicaitong.repository.UserRepository;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
/** 提供租户主账号注册和统一账号登录接口，并签发服务端会话令牌。 */
public class AuthController {
  private final TenantRepository tenants;
  private final UserRepository users;
  private final SessionRepository sessions;

  record Register(
      @NotBlank String company,
      @NotBlank String name,
      @NotBlank String account,
      @Pattern(regexp = "^1[3-9]\\d{9}$", message = "请输入正确的手机号码") String phone,
      @Size(min = 6) String password) {}

  record Login(@NotBlank String account, @NotBlank String password) {}

  record AuthView(String token, UUID userId, String name, String company, Domain.Role role) {}

  /** 创建新租户及其唯一主账号。账号全局唯一，整个过程在同一事务内完成。 */
  @PostMapping("/register")
  @Transactional
  AuthView register(@Valid @RequestBody Register r) {
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
    u.setPhone(r.phone());
    u.setRole(Domain.Role.ADMIN);
    u.setOwner(true);
    users.save(u);
    return issue(u, t.getName());
  }

  /** 校验账号状态和密码，成功后签发七天有效的随机会话令牌。 */
  @PostMapping("/login")
  AuthView login(@Valid @RequestBody Login r) {
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
