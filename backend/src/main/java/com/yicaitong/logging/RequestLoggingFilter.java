package com.yicaitong.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {
  private static final String REQUEST_ID_HEADER = "X-Request-Id";
  private static final String REQUEST_ID_MDC_KEY = "requestId";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String requestId =
        Optional.ofNullable(request.getHeader(REQUEST_ID_HEADER))
            .filter(value -> !value.isBlank())
            .map(value -> value.substring(0, Math.min(value.length(), 64)))
            .orElseGet(() -> UUID.randomUUID().toString().replace("-", ""));
    long startedAt = System.nanoTime();

    MDC.put(REQUEST_ID_MDC_KEY, requestId);
    response.setHeader(REQUEST_ID_HEADER, requestId);
    try {
      filterChain.doFilter(request, response);
    } finally {
      long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000;
      log.info(
          "HTTP {} {} status={} durationMs={} clientIp={}",
          request.getMethod(),
          request.getRequestURI(),
          response.getStatus(),
          elapsedMillis,
          resolveClientIp(request));
      MDC.remove(REQUEST_ID_MDC_KEY);
    }
  }

  private String resolveClientIp(HttpServletRequest request) {
    String forwardedFor = request.getHeader("X-Forwarded-For");
    if (forwardedFor != null && !forwardedFor.isBlank()) {
      return forwardedFor.split(",", 2)[0].trim();
    }
    return request.getRemoteAddr();
  }
}
