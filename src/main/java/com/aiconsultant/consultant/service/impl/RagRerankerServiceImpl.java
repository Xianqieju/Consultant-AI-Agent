package com.aiconsultant.consultant.service.impl;

import com.aiconsultant.consultant.service.RagRerankerService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class RagRerankerServiceImpl implements RagRerankerService {

    @Value("${app.rag.rerank.endpoint:}")
    private String rerankEndpoint;
    @Value("${app.rag.rerank.api-key:}")
    private String rerankApiKey;
    @Value("${app.rag.rerank.model:bge-reranker-v2-m3}")
    private String rerankModel;
    @Value("${app.rag.rerank.timeout-ms:2500}")
    private int rerankTimeoutMs;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public Map<Integer, Double> rerank(String query, List<String> documents) {
        if (query == null || query.isBlank() || documents == null || documents.isEmpty()) {
            return Map.of();
        }
        if (rerankEndpoint == null || rerankEndpoint.isBlank()) {
            return Map.of();
        }

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("model", rerankModel);
            payload.put("query", query);
            payload.put("documents", documents);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (rerankApiKey != null && !rerankApiKey.isBlank()) {
                headers.setBearerAuth(rerankApiKey);
            }
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);

            SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
            rf.setConnectTimeout(rerankTimeoutMs);
            rf.setReadTimeout(rerankTimeoutMs);
            RestTemplate rt = new RestTemplate(rf);
            ResponseEntity<String> response = rt.postForEntity(rerankEndpoint, entity, String.class);
            String body = response.getBody();
            if (body == null || body.isBlank()) {
                return Map.of();
            }
            return parseRerankScores(body);
        } catch (Exception e) {
            log.warn("BGE rerank 调用失败，回退本地精排: {}", e.getMessage());
            return Map.of();
        }
    }

    private Map<Integer, Double> parseRerankScores(String body) {
        Map<Integer, Double> scores = new HashMap<>();
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode arr = firstArray(root, "results", "data");
            if (arr == null) {
                JsonNode output = root.path("output");
                arr = firstArray(output, "results", "data");
            }
            if (arr == null || !arr.isArray()) {
                return Map.of();
            }
            for (JsonNode n : arr) {
                int idx = readInt(n, "index", "document_index");
                if (idx < 0) {
                    continue;
                }
                double score = readDouble(n, "relevance_score", "score", "relevanceScore");
                scores.put(idx, score);
            }
            return scores;
        } catch (Exception e) {
            log.warn("BGE rerank 响应解析失败，回退本地精排: {}", e.getMessage());
            return Map.of();
        }
    }

    private static JsonNode firstArray(JsonNode root, String k1, String k2) {
        if (root == null || root.isMissingNode()) {
            return null;
        }
        JsonNode a1 = root.get(k1);
        if (a1 != null && a1.isArray()) {
            return a1;
        }
        JsonNode a2 = root.get(k2);
        if (a2 != null && a2.isArray()) {
            return a2;
        }
        return null;
    }

    private static int readInt(JsonNode node, String... keys) {
        for (String k : keys) {
            JsonNode v = node.get(k);
            if (v != null && v.isNumber()) {
                return v.asInt(-1);
            }
        }
        return -1;
    }

    private static double readDouble(JsonNode node, String... keys) {
        for (String k : keys) {
            JsonNode v = node.get(k);
            if (v != null && v.isNumber()) {
                return v.asDouble(0.0);
            }
        }
        return 0.0;
    }
}
