package com.aiconsultant.consultant.util;

import dev.langchain4j.data.embedding.Embedding;

import java.util.HashSet;
import java.util.Set;

/**
 * 粗排用字符 n-gram Jaccard；精排用向量余弦（与 {@link dev.langchain4j.model.embedding.EmbeddingModel} 维度一致）。
 */
public final class RagTextScoring {

    private RagTextScoring() {
    }

    public static double cosine(Embedding a, Embedding b) {
        float[] v1 = a.vector();
        float[] v2 = b.vector();
        if (v1.length != v2.length) {
            return 0;
        }
        double dot = 0, n1 = 0, n2 = 0;
        for (int i = 0; i < v1.length; i++) {
            double x = v1[i];
            double y = v2[i];
            dot += x * y;
            n1 += x * x;
            n2 += y * y;
        }
        double den = Math.sqrt(n1) * Math.sqrt(n2);
        return den < 1e-9 ? 0 : dot / den;
    }

    /**
     * 中文/混合文本可用的轻量重叠度（2-gram Jaccard），用于粗排 BM25 的简化替代。
     */
    public static double jaccardCharBigrams(String a, String b) {
        if (a == null || b == null || a.isBlank() || b.isBlank()) {
            return 0;
        }
        String x = a.replaceAll("\\s+", "");
        String y = b.replaceAll("\\s+", "");
        Set<String> sa = charNgrams(x, 2);
        Set<String> sb = charNgrams(y, 2);
        if (sa.isEmpty() && sb.isEmpty()) {
            return 0;
        }
        Set<String> inter = new HashSet<>(sa);
        inter.retainAll(sb);
        Set<String> union = new HashSet<>(sa);
        union.addAll(sb);
        return union.isEmpty() ? 0 : (double) inter.size() / union.size();
    }

    private static Set<String> charNgrams(String s, int n) {
        Set<String> out = new HashSet<>();
        if (s.length() < n) {
            out.add(s);
            return out;
        }
        for (int i = 0; i + n <= s.length(); i++) {
            out.add(s.substring(i, i + n));
        }
        return out;
    }
}
