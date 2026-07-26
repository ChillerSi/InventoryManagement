package com.yicaitong.security;

import com.yicaitong.domain.Domain.Session;
import com.yicaitong.exception.ApiException;
import com.yicaitong.repository.SessionRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
/** 校验 Bearer 会话令牌，并从数据库会话构建不可由客户端覆盖的租户上下文。 */
public class AuthFilter extends OncePerRequestFilter {
  private final SessionRepository sessions;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    try {
      String path = request.getRequestURI();
      if (!path.startsWith("/api/") || path.startsWith("/api/auth/")) {
        filterChain.doFilter(request, response);
        return;
      }

      String authorization = request.getHeader("Authorization");
      if (authorization == null || !authorization.startsWith("Bearer ")) {
        throw new ApiException(HttpStatus.UNAUTHORIZED, "请先登录");
      }

      Session session =
          sessions
              .findById(authorization.substring(7))
              .filter(value -> value.getExpiresAt().isAfter(LocalDateTime.now()))
              .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "登录已失效"));
      UserContext.set(
          new CurrentUser(session.getUserId(), session.getTenantId(), session.getRole()));
      filterChain.doFilter(request, response);
    } finally {
      UserContext.clear();
    }
  }
}
