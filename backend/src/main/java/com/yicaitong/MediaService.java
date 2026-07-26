package com.yicaitong;

import com.fasterxml.jackson.databind.*;
import io.minio.*;
import io.minio.http.Method;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service @RequiredArgsConstructor
class MediaService {
  @Value("${app.minio.endpoint}") String endpoint; @Value("${app.minio.access-key}") String access;
  @Value("${app.minio.secret-key}") String secret; @Value("${app.minio.bucket}") String bucket;
  MinioClient client;
  @PostConstruct void init() throws Exception {
    client=MinioClient.builder().endpoint(endpoint).credentials(access,secret).build();
    if(!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
  }
  String put(MultipartFile file,String key) throws Exception {
    client.putObject(PutObjectArgs.builder().bucket(bucket).object(key).stream(file.getInputStream(),file.getSize(),-1).contentType(file.getContentType()).build()); return key;
  }
  String url(String key) {
    if(key==null)return null;
    try{return client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder().method(Method.GET).bucket(bucket).object(key).expiry(1,TimeUnit.HOURS).build());}
    catch(Exception e){return null;}
  }
}

@Service
class VectorService {
  @Value("${app.qdrant-url}") String qdrant; @Value("${app.embedding-url}") String embedding;
  @Value("${app.search-threshold}") double threshold;
  private final RestClient rest=RestClient.create(); private final ObjectMapper json=new ObjectMapper();
  List<Double> embed(byte[] bytes,String contentType) {
    MultipartBodyBuilder builder=new MultipartBodyBuilder();
    builder.part("file",new ByteArrayResource(bytes){public String getFilename(){return "crop.jpg";}})
      .contentType(MediaType.parseMediaType(contentType==null?"image/jpeg":contentType));
    JsonNode node=rest.post().uri(embedding+"/embed").contentType(MediaType.MULTIPART_FORM_DATA)
      .body(builder.build()).retrieve().body(JsonNode.class);
    List<Double> out=new ArrayList<>(); node.get("vector").forEach(v->out.add(v.asDouble())); return out;
  }
  void ensure(int size) {
    try { rest.put().uri(qdrant+"/collections/products").contentType(MediaType.APPLICATION_JSON)
      .body(Map.of("vectors",Map.of("size",size,"distance","Cosine"))).retrieve().toBodilessEntity(); } catch(Exception ignored){}
  }
  void index(UUID imageId,UUID tenantId,UUID productId,List<Double> vector) {
    ensure(vector.size()); rest.put().uri(qdrant+"/collections/products/points?wait=true").contentType(MediaType.APPLICATION_JSON)
      .body(Map.of("points",List.of(Map.of("id",imageId.toString(),"vector",vector,"payload",Map.of("tenantId",tenantId.toString(),"productId",productId.toString())))))
      .retrieve().toBodilessEntity();
  }
  List<Map<String,Object>> search(UUID tenantId,List<Double> vector,int top) {
    JsonNode n=rest.post().uri(qdrant+"/collections/products/points/search").contentType(MediaType.APPLICATION_JSON)
      .body(Map.of("vector",vector,"limit",100,"score_threshold",threshold,"with_payload",true,
        "filter",Map.of("must",List.of(Map.of("key","tenantId","match",Map.of("value",tenantId.toString()))))))
      .retrieve().body(JsonNode.class);
    Map<String,Double> best=new HashMap<>();
    n.get("result").forEach(x->best.merge(x.get("payload").get("productId").asText(),x.get("score").asDouble(),Math::max));
    return best.entrySet().stream().sorted(Map.Entry.<String,Double>comparingByValue().reversed()).limit(top)
      .map(e->Map.<String,Object>of("productId",e.getKey(),"similarity",e.getValue())).toList();
  }
}
