package com.yicaitong.controller;

import com.yicaitong.domain.Domain;
import com.yicaitong.domain.Domain.User;
import com.yicaitong.repository.TenantRepository;
import com.yicaitong.repository.UserRepository;
import com.yicaitong.security.UserContext;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

record UserDto(
    UUID id, String name, String account, Domain.Role role, boolean active, boolean owner) {}

record UserInput(String name, String account, String password, Domain.Role role, Boolean active) {}

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
/** 管理当前租户的团队子账号；主账号不可停用、变更角色或删除。 */
public class ProfileController {
  private final UserRepository users;
  private final TenantRepository tenants;

  /** 查询当前租户的主账号和全部子账号，仅管理员可以访问。 */
  @GetMapping
  List<UserDto> list() {
    UserContext.require(Domain.Role.ADMIN);
    return users.findByTenantIdOrderByOwnerDescName(UserContext.get().tenantId()).stream()
        .map(this::dto)
        .toList();
  }

  /** 创建管理员、运营、买手或查看者子账号。 */
  @PostMapping
  UserDto create(@RequestBody UserInput in) {
    UserContext.require(Domain.Role.ADMIN);
    if (in.password() == null || in.password().length() < 6) {
      throw new IllegalArgumentException("登录密码至少需要 6 位");
    }
    users
        .findByLoginAccountIgnoreCase(in.account())
        .ifPresent(
            existing -> {
              throw new IllegalArgumentException("登录账号已存在");
            });
    User u = new User();
    u.setTenantId(UserContext.get().tenantId());
    u.setName(in.name());
    u.setLoginAccount(in.account().toLowerCase());
    u.setPassword(in.password());
    u.setRole(in.role());
    u.setActive(in.active() == null || in.active());
    users.save(u);
    return dto(u);
  }

  /** 修改子账号姓名、密码、角色或启用状态。 */
  @PatchMapping("/{id}")
  UserDto edit(@PathVariable UUID id, @RequestBody UserInput in) {
    UserContext.require(Domain.Role.ADMIN);
    User u =
        users
            .findById(id)
            .filter(x -> x.getTenantId().equals(UserContext.get().tenantId()))
            .orElseThrow();
    if (u.isOwner() && !u.getId().equals(UserContext.get().userId())) {
      throw new IllegalStateException("只有主账号本人可以修改主账号资料");
    }
    if (in.name() != null) u.setName(in.name());
    if (in.password() != null && !in.password().isBlank()) {
      if (in.password().length() < 6) throw new IllegalArgumentException("登录密码至少需要 6 位");
      u.setPassword(in.password());
    }
    if (!u.isOwner() && in.role() != null) u.setRole(in.role());
    if (!u.isOwner() && in.active() != null) u.setActive(in.active());
    users.save(u);
    return dto(u);
  }

  /** 管理员修改采购公司或团队名称。 */
  @PatchMapping("/company")
  Map<String, String> updateCompany(@RequestBody Map<String, String> input) {
    UserContext.require(Domain.Role.ADMIN);
    String name = Objects.toString(input.get("name"), "").trim();
    if (name.isBlank()) throw new IllegalArgumentException("采购主体名称不能为空");
    var tenant = tenants.findById(UserContext.get().tenantId()).orElseThrow();
    tenant.setName(name);
    tenants.save(tenant);
    return Map.of("name", name);
  }

  /** 删除当前租户的子账号，主账号受到服务端保护。 */
  @DeleteMapping("/{id}")
  void delete(@PathVariable UUID id) {
    UserContext.require(Domain.Role.ADMIN);
    User u =
        users
            .findById(id)
            .filter(x -> x.getTenantId().equals(UserContext.get().tenantId()))
            .orElseThrow();
    if (u.isOwner()) throw new IllegalStateException("主账号不可删除");
    users.delete(u);
  }

  UserDto dto(User u) {
    return new UserDto(
        u.getId(), u.getName(), u.getLoginAccount(), u.getRole(), u.isActive(), u.isOwner());
  }
}
