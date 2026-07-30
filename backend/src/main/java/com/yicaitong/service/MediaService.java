package com.yicaitong.service;

import io.minio.*;
import jakarta.annotation.PostConstruct;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
/** 封装 MinIO Bucket 初始化、私有对象上传、应用层短期媒体签名和对象流读取。 */
public class MediaService {
  @Value("${app.minio.endpoint}")
  String endpoint;

  @Value("${app.minio.access-key}")
  String access;

  @Value("${app.minio.secret-key}")
  String secret;

  @Value("${app.minio.bucket}")
  String bucket;

  @Value("${app.media-signing-secret}")
  String signingSecret;

  MinioClient client;

  @PostConstruct
  void init() throws Exception {
    client = MinioClient.builder().endpoint(endpoint).credentials(access, secret).build();
    if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build()))
      client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
  }

  /** 将图片写入由服务端生成的租户隔离对象键。 */
  public String put(MultipartFile file, String key) throws Exception {
    client.putObject(
        PutObjectArgs.builder().bucket(bucket).object(key).stream(
                file.getInputStream(), file.getSize(), -1)
            .contentType(file.getContentType())
            .build());
    return key;
  }

  /** 尝试删除已被新图片替换的旧对象；清理失败只记录日志，不影响新图片生效。 */
  public void deleteQuietly(String key) {
    if (key == null || key.isBlank()) return;
    try {
      client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build());
    } catch (Exception exception) {
      log.warn("MinIO 旧图片清理失败: objectKey={}", key, exception);
    }
  }

  /** 为商品图片生成一小时有效的应用层签名地址，不暴露 MinIO 对象键和登录令牌。 */
  public String productImageUrl(UUID imageId, UUID tenantId) {
    return signedUrl("product-images", imageId, tenantId);
  }

  /** 为店铺门头生成一小时有效的应用层签名地址。 */
  public String storefrontUrl(UUID storeId, UUID tenantId) {
    return signedUrl("storefronts", storeId, tenantId);
  }

  /** 校验媒体地址的租户、资源类型、业务主键、有效期和 HMAC 签名。 */
  public boolean validSignature(
      String resourceType, UUID resourceId, UUID tenantId, long expires, String signature) {
    long now = Instant.now().getEpochSecond();
    if (signature == null || expires < now || expires > now + 3660) return false;
    byte[] expected = sign(payload(resourceType, resourceId, tenantId, expires));
    byte[] actual;
    try {
      actual = Base64.getUrlDecoder().decode(signature);
    } catch (IllegalArgumentException exception) {
      return false;
    }
    return MessageDigest.isEqual(expected, actual);
  }

  /** 打开 MinIO 私有对象流；调用方必须关闭返回的输入流。 */
  public MediaObject open(String objectKey) throws Exception {
    StatObjectResponse metadata =
        client.statObject(StatObjectArgs.builder().bucket(bucket).object(objectKey).build());
    InputStream stream =
        client.getObject(GetObjectArgs.builder().bucket(bucket).object(objectKey).build());
    return new MediaObject(
        stream, metadata.size(), metadata.contentType(), metadata.etag(), metadata.lastModified());
  }

  private String signedUrl(String resourceType, UUID resourceId, UUID tenantId) {
    long expires = Instant.now().plusSeconds(3600).getEpochSecond();
    String signature =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(sign(payload(resourceType, resourceId, tenantId, expires)));
    return "/api/media/"
        + resourceType
        + "/"
        + resourceId
        + "?tenant="
        + tenantId
        + "&expires="
        + expires
        + "&signature="
        + signature;
  }

  private String payload(String resourceType, UUID resourceId, UUID tenantId, long expires) {
    return resourceType + ":" + resourceId + ":" + tenantId + ":" + expires;
  }

  private byte[] sign(String value) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(signingSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
    } catch (Exception exception) {
      throw new IllegalStateException("无法生成媒体访问签名", exception);
    }
  }

  public record MediaObject(
      InputStream stream,
      long contentLength,
      String contentType,
      String etag,
      java.time.ZonedDateTime lastModified) {}
}
