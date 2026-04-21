package com.aiconsultant.consultant.rag.coalesce;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link EmbeddingSearchResult} 与 JSON 缓存载荷互转；metadata 以字符串键值对保留 RAG 所需键。
 */
@Component
public class RagVectorSearchResultCodec {

    private final ObjectMapper objectMapper;

    public RagVectorSearchResultCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public EmbeddingSearchResult<TextSegment> fromJson(String json) throws JsonProcessingException {
        RagVectorSearchCachePayload p = objectMapper.readValue(json, RagVectorSearchCachePayload.class);
        if (p.getV() != RagVectorSearchCachePayload.VERSION || p.getMatches() == null) {
            throw new IllegalArgumentException("invalid rag vector cache payload");
        }
        List<EmbeddingMatch<TextSegment>> out = new ArrayList<>();
        for (RagVectorSearchCachePayload.RagVectorSearchMatchPayload m : p.getMatches()) {
            if (m == null) {
                continue;
            }
            float[] vec = m.getEmbedding();
            Embedding emb = vec == null ? null : Embedding.from(vec);
            Metadata meta;
            if (m.getMetadata() == null || m.getMetadata().isEmpty()) {
                meta = new Metadata();
            } else {
                Map<String, Object> raw = new HashMap<>(m.getMetadata());
                meta = Metadata.from(raw);
            }
            TextSegment seg = TextSegment.from(
                    m.getText() == null ? "" : m.getText(),
                    meta
            );
            out.add(new EmbeddingMatch<>(m.getScore(), m.getEmbeddingId(), emb, seg));
        }
        return new EmbeddingSearchResult<>(out);
    }

    public String toJson(RagVectorSearchCachePayload payload) throws JsonProcessingException {
        return objectMapper.writeValueAsString(payload);
    }

    public int utf8ByteLength(String json) {
        return json.getBytes(StandardCharsets.UTF_8).length;
    }

    /**
     * 轻量读取 modelId，避免错误模型缓存被反序列化。
     */
    public String peekModelId(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode mid = root.get("modelId");
            return mid != null && mid.isTextual() ? mid.asText() : null;
        } catch (Exception e) {
            return null;
        }
    }

    public RagVectorSearchCachePayload toPayload(String embeddingModelId, EmbeddingSearchResult<TextSegment> result, int maxMatches) {
        RagVectorSearchCachePayload p = new RagVectorSearchCachePayload();
        p.setModelId(embeddingModelId);
        List<RagVectorSearchCachePayload.RagVectorSearchMatchPayload> list = new ArrayList<>();
        List<EmbeddingMatch<TextSegment>> src = result.matches();
        int n = Math.min(src.size(), Math.max(0, maxMatches));
        for (int i = 0; i < n; i++) {
            EmbeddingMatch<TextSegment> em = src.get(i);
            RagVectorSearchCachePayload.RagVectorSearchMatchPayload row = new RagVectorSearchCachePayload.RagVectorSearchMatchPayload();
            row.setScore(em.score());
            row.setEmbeddingId(em.embeddingId());
            if (em.embedding() != null) {
                row.setEmbedding(em.embedding().vector());
            }
            if (em.embedded() != null) {
                row.setText(em.embedded().text());
                Metadata md = em.embedded().metadata();
                if (md != null) {
                    Map<String, String> strMap = new HashMap<>();
                    for (Map.Entry<String, Object> e : md.toMap().entrySet()) {
                        Object v = e.getValue();
                        if (v != null) {
                            strMap.put(e.getKey(), String.valueOf(v));
                        }
                    }
                    row.setMetadata(strMap);
                }
            }
            list.add(row);
        }
        p.setMatches(list);
        return p;
    }
}
