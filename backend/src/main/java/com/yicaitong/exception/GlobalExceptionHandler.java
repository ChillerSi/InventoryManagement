package com.yicaitong.exception;

import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
  /** 将请求体字段校验错误转换为前端可直接展示的 400 响应。 */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<Map<String, Object>> handleValidationException(
      MethodArgumentNotValidException exception) {
    String message =
        exception.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(error -> error.getDefaultMessage())
            .orElse("请求参数不正确");
    log.warn("请求参数校验失败: message={}", message);
    return ResponseEntity.badRequest().body(Map.of("message", message));
  }

  @ExceptionHandler(ApiException.class)
  ResponseEntity<Map<String, Object>> handleApiException(ApiException exception) {
    log.warn(
        "业务请求失败: status={}, message={}", exception.getStatus().value(), exception.getMessage());
    return ResponseEntity.status(exception.getStatus())
        .body(Map.of("message", exception.getMessage()));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<Map<String, Object>> handleUnknownException(Exception exception) {
    log.error("未处理的服务端异常", exception);
    return ResponseEntity.internalServerError()
        .body(Map.of("message", Optional.ofNullable(exception.getMessage()).orElse("服务器内部错误")));
  }
}
