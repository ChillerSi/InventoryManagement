package com.yicaitong;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.filter.OncePerRequestFilter;

record CurrentUser(UUID userId, UUID tenantId, Domain.Role role) {}

final class UserContext {
  private static final ThreadLocal<CurrentUser> CURRENT = new ThreadLocal<>();

  static void set(CurrentUser user) {
    CURRENT.set(user);
  }

  static CurrentUser get() {
    if (CURRENT.get() == null) throw new ApiException(HttpStatus.UNAUTHORIZED, "请先登录");
    return CURRENT.get();
  }

  static void clear() {
    CURRENT.remove();
  }

  static void require(Domain.Role... roles) {
    if (Arrays.stream(roles).noneMatch(r -> r == get().role()))
      throw new ApiException(HttpStatus.FORBIDDEN, "无权执行此操作");
  }
}

class ApiException extends RuntimeException {
  final HttpStatus status;

  ApiException(HttpStatus status, String message) {
    super(message);
    this.status = status;
  }
}

@Component
@RequiredArgsConstructor
class AuthFilter extends OncePerRequestFilter {
  private final SessionRepository sessions;

  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    try {
      String path = req.getRequestURI();
      if (!path.startsWith("/api/") || path.startsWith("/api/auth/")) {
        chain.doFilter(req, res);
        return;
      }
      String header = req.getHeader("Authorization");
      if (header == null || !header.startsWith("Bearer "))
        throw new ApiException(HttpStatus.UNAUTHORIZED, "请先登录");
      Session s =
          sessions
              .findById(header.substring(7))
              .filter(x -> x.getExpiresAt().isAfter(LocalDateTime.now()))
              .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "登录已失效"));
      UserContext.set(new CurrentUser(s.getUserId(), s.getTenantId(), s.getRole()));
      chain.doFilter(req, res);
    } finally {
      UserContext.clear();
    }
  }
}

@RestControllerAdvice
class Errors {
  @ExceptionHandler(ApiException.class)
  @ResponseStatus
  org.springframework.http.ResponseEntity<Map<String, Object>> api(ApiException e) {
    return org.springframework.http.ResponseEntity.status(e.status)
        .body(Map.of("message", e.getMessage()));
  }

  @ExceptionHandler(Exception.class)
  org.springframework.http.ResponseEntity<Map<String, Object>> unknown(Exception e) {
    return org.springframework.http.ResponseEntity.status(500)
        .body(Map.of("message", Optional.ofNullable(e.getMessage()).orElse("服务器错误")));
  }
}
