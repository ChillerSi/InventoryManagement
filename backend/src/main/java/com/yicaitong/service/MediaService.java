package com.yicaitong.service;

import io.minio.*;
import io.minio.http.Method;
import jakarta.annotation.PostConstruct;
import java.io.*;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
/** 封装 MinIO Bucket 初始化、私有对象上传和短时下载地址签发。 */
public class MediaService {
  @Value("${app.minio.endpoint}")
  String endpoint;

  @Value("${app.minio.access-key}")
  String access;

  @Value("${app.minio.secret-key}")
  String secret;

  @Value("${app.minio.bucket}")
  String bucket;

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

  /** 为私有对象生成一小时有效的 GET 预签名地址；数据库不会持久化该地址。 */
  public String url(String key) {
    if (key == null) return null;
    try {
      return client.getPresignedObjectUrl(
          GetPresignedObjectUrlArgs.builder()
              .method(Method.GET)
              .bucket(bucket)
              .object(key)
              .expiry(1, TimeUnit.HOURS)
              .build());
    } catch (Exception e) {
      return null;
    }
  }
}
