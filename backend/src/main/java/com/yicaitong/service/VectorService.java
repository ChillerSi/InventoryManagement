package com.yicaitong.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
/** 封装 SigLIP 向量服务和 Qdrant 索引、检索调用，不承担业务权限判断。 */
public class VectorService {
  @Value("${app.qdrant-url}")
  String qdrant;

  @Value("${app.embedding-url}")
  String embedding;

  @Value("${app.search-threshold}")
  double threshold;

  private final RestClient rest = RestClient.create();

  /** 调用 Python SigLIP 服务，将裁剪后的图片转换为归一化向量。 */
  public List<Double> embed(byte[] bytes, String contentType) {
    MultipartBodyBuilder builder = new MultipartBodyBuilder();
    builder
        .part(
            "file",
            new ByteArrayResource(bytes) {
              @Override
              public String getFilename() {
                return "crop.jpg";
              }
            })
        .contentType(MediaType.parseMediaType(contentType == null ? "image/jpeg" : contentType));
    JsonNode node =
        rest.post()
            .uri(embedding + "/embed")
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(builder.build())
            .retrieve()
            .body(JsonNode.class);
    List<Double> result = new ArrayList<>();
    node.get("vector").forEach(value -> result.add(value.asDouble()));
    return result;
  }

  /** 将图片向量写入 Qdrant，并保存租户、商品和图片标识作为过滤 payload。 */
  public void index(UUID imageId, UUID tenantId, UUID productId, List<Double> vector) {
    ensureCollection(vector.size());
    rest.put()
        .uri(qdrant + "/collections/products/points?wait=true")
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            Map.of(
                "points",
                List.of(
                    Map.of(
                        "id",
                        imageId.toString(),
                        "vector",
                        vector,
                        "payload",
                        Map.of(
                            "tenantId", tenantId.toString(), "productId", productId.toString())))))
        .retrieve()
        .toBodilessEntity();
  }

  /** 在 Qdrant 中强制按 tenantId 过滤并按商品聚合最高相似度。 */
  public List<Map<String, Object>> search(UUID tenantId, List<Double> vector, int top) {
    JsonNode response =
        rest.post()
            .uri(qdrant + "/collections/products/points/search")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                Map.of(
                    "vector",
                    vector,
                    "limit",
                    100,
                    "score_threshold",
                    threshold,
                    "with_payload",
                    true,
                    "filter",
                    Map.of(
                        "must",
                        List.of(
                            Map.of(
                                "key",
                                "tenantId",
                                "match",
                                Map.of("value", tenantId.toString()))))))
            .retrieve()
            .body(JsonNode.class);
    Map<String, Double> bestScoreByProduct = new HashMap<>();
    response
        .get("result")
        .forEach(
            hit ->
                bestScoreByProduct.merge(
                    hit.get("payload").get("productId").asText(),
                    hit.get("score").asDouble(),
                    Math::max));
    return bestScoreByProduct.entrySet().stream()
        .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
        .limit(top)
        .map(
            entry ->
                Map.<String, Object>of("productId", entry.getKey(), "similarity", entry.getValue()))
        .toList();
  }

  private void ensureCollection(int vectorSize) {
    try {
      rest.put()
          .uri(qdrant + "/collections/products")
          .contentType(MediaType.APPLICATION_JSON)
          .body(Map.of("vectors", Map.of("size", vectorSize, "distance", "Cosine")))
          .retrieve()
          .toBodilessEntity();
    } catch (Exception ignored) {
      // Qdrant returns an error when the collection already exists.
    }
  }
}
