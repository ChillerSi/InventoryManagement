package com.yicaitong.security;

import com.yicaitong.domain.Domain.Role;
import com.yicaitong.exception.ApiException;
import java.util.Arrays;
import org.springframework.http.HttpStatus;

/** 保存单次请求的用户、租户和角色信息，为数据隔离及接口授权提供统一入口。 */
public final class UserContext {
  private static final ThreadLocal<CurrentUser> CURRENT = new ThreadLocal<>();

  private UserContext() {}

  static void set(CurrentUser user) {
    CURRENT.set(user);
  }

  /** 获取当前请求身份；未登录请求会立即返回 401。 */
  public static CurrentUser get() {
    if (CURRENT.get() == null) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "请先登录");
    }
    return CURRENT.get();
  }

  static void clear() {
    CURRENT.remove();
  }

  /** 校验当前用户是否拥有任一允许角色，不满足时返回 403。 */
  public static void require(Role... roles) {
    if (Arrays.stream(roles).noneMatch(role -> role == get().role())) {
      throw new ApiException(HttpStatus.FORBIDDEN, "无权执行此操作");
    }
  }
}
