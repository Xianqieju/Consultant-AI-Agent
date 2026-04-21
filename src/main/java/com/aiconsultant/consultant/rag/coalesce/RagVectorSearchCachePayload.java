package com.aiconsultant.consultant.rag.coalesce;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

/**
 * Redis 中缓存的向量召回命中（与 LangChain4j 类型解耦，便于演进）。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RagVectorSearchCachePayload {

    public static final int VERSION = 1;

    private int v = VERSION;
    private String modelId;
    private List<RagVectorSearchMatchPayload> matches;

    public int getV() {
        return v;
    }

    public void setV(int v) {
        this.v = v;
    }

    public String getModelId() {
        return modelId;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
    }

    public List<RagVectorSearchMatchPayload> getMatches() {
        return matches;
    }

    public void setMatches(List<RagVectorSearchMatchPayload> matches) {
        this.matches = matches;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class RagVectorSearchMatchPayload {
        private Double score;
        private String embeddingId;
        private float[] embedding;
        private String text;
        private Map<String, String> metadata;

        public Double getScore() {
            return score;
        }

        public void setScore(Double score) {
            this.score = score;
        }

        public String getEmbeddingId() {
            return embeddingId;
        }

        public void setEmbeddingId(String embeddingId) {
            this.embeddingId = embeddingId;
        }

        public float[] getEmbedding() {
            return embedding;
        }

        public void setEmbedding(float[] embedding) {
            this.embedding = embedding;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        public Map<String, String> getMetadata() {
            return metadata;
        }

        public void setMetadata(Map<String, String> metadata) {
            this.metadata = metadata;
        }
    }
}
