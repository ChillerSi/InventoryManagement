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

  public String put(MultipartFile file, String key) throws Exception {
    client.putObject(
        PutObjectArgs.builder().bucket(bucket).object(key).stream(
                file.getInputStream(), file.getSize(), -1)
            .contentType(file.getContentType())
            .build());
    return key;
  }

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
