package com.aiconsultant.consultant.rag.coalesce;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * 向量召回合并键：不包含 userId / expert；与首轮 {@code EmbeddingSearchRequest} 语义一致。
 */
public final class RagVectorFlightKey {

    private final String normalizedQuery;
    private final int maxResults;
    private final double minScore;
    private final String embeddingModelId;

    private RagVectorFlightKey(String normalizedQuery, int maxResults, double minScore, String embeddingModelId) {
        this.normalizedQuery = Objects.requireNonNull(normalizedQuery);
        this.maxResults = maxResults;
        this.minScore = minScore;
        this.embeddingModelId = embeddingModelId == null ? "" : embeddingModelId;
    }

    public static String normalizeQuery(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().replaceAll("\\s+", " ");
    }

    public static RagVectorFlightKey of(String query, int maxResults, double minScore, String embeddingModelId) {
        return new RagVectorFlightKey(normalizeQuery(query), maxResults, minScore, embeddingModelId);
    }

    public String normalizedQuery() {
        return normalizedQuery;
    }

    public int maxResults() {
        return maxResults;
    }

    public double minScore() {
        return minScore;
    }

    public String embeddingModelId() {
        return embeddingModelId;
    }

    /**
     * 用于 CHM；长 query 仍用内容参与 equals，避免哈希碰撞误合并。
     */
    public String stableIdentityString() {
        return embeddingModelId + "\u0001" + normalizedQuery + "\u0001" + maxResults + "\u0001" + minScore;
    }

    /**
     * 固定长度 Redis key 后缀（SHA-256 hex）。
     */
    public String redisHashSuffix() {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(stableIdentityString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        RagVectorFlightKey that = (RagVectorFlightKey) o;
        return maxResults == that.maxResults
                && Double.compare(that.minScore, minScore) == 0
                && normalizedQuery.equals(that.normalizedQuery)
                && embeddingModelId.equals(that.embeddingModelId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(normalizedQuery, maxResults, minScore, embeddingModelId);
    }
}
