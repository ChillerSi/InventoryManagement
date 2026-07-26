package com.yicaitong.security;

import com.yicaitong.domain.Domain.Role;
import com.yicaitong.exception.ApiException;
import java.util.Arrays;
import org.springframework.http.HttpStatus;

public final class UserContext {
  private static final ThreadLocal<CurrentUser> CURRENT = new ThreadLocal<>();

  private UserContext() {}

  static void set(CurrentUser user) {
    CURRENT.set(user);
  }

  public static CurrentUser get() {
    if (CURRENT.get() == null) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "请先登录");
    }
    return CURRENT.get();
  }

  static void clear() {
    CURRENT.remove();
  }

  public static void require(Role... roles) {
    if (Arrays.stream(roles).noneMatch(role -> role == get().role())) {
      throw new ApiException(HttpStatus.FORBIDDEN, "无权执行此操作");
    }
  }
}
