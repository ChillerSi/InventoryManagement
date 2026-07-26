package com.yicaitong;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.*;

record UserDto(UUID id,String name,String account,Domain.Role role,boolean active,boolean owner){}
record UserInput(String name,String account,String password,Domain.Role role,Boolean active){}
@RestController @RequestMapping("/api/users") @RequiredArgsConstructor
class ProfileController {
  private final UserRepository users;
  @GetMapping List<UserDto> list(){UserContext.require(Domain.Role.ADMIN);return users.findByTenantIdOrderByOwnerDescName(UserContext.get().tenantId()).stream().map(this::dto).toList();}
  @PostMapping UserDto create(@RequestBody UserInput in){UserContext.require(Domain.Role.ADMIN);if(in.role()==Domain.Role.ADMIN)throw new IllegalArgumentException("不能创建第二个管理员");User u=new User();u.setTenantId(UserContext.get().tenantId());u.setName(in.name());u.setLoginAccount(in.account().toLowerCase());u.setPassword(in.password());u.setRole(in.role());u.setActive(in.active()==null||in.active());users.save(u);return dto(u);}
  @PatchMapping("/{id}") UserDto edit(@PathVariable UUID id,@RequestBody UserInput in){UserContext.require(Domain.Role.ADMIN);User u=users.findById(id).filter(x->x.getTenantId().equals(UserContext.get().tenantId())).orElseThrow();if(u.isOwner())throw new IllegalStateException("主账号不可修改");if(in.name()!=null)u.setName(in.name());if(in.password()!=null&&!in.password().isBlank())u.setPassword(in.password());if(in.role()!=null&&in.role()!=Domain.Role.ADMIN)u.setRole(in.role());if(in.active()!=null)u.setActive(in.active());users.save(u);return dto(u);}
  @DeleteMapping("/{id}") void delete(@PathVariable UUID id){UserContext.require(Domain.Role.ADMIN);User u=users.findById(id).filter(x->x.getTenantId().equals(UserContext.get().tenantId())).orElseThrow();if(u.isOwner())throw new IllegalStateException("主账号不可删除");users.delete(u);}
  UserDto dto(User u){return new UserDto(u.getId(),u.getName(),u.getLoginAccount(),u.getRole(),u.isActive(),u.isOwner());}
}
