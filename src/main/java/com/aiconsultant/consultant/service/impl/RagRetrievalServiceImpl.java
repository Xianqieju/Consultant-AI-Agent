package com.aiconsultant.consultant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.aiconsultant.consultant.entity.RagContentChunk;
import com.aiconsultant.consultant.entity.RagDocument;
import com.aiconsultant.consultant.mapper.RagContentChunkMapper;
import com.aiconsultant.consultant.mapper.RagDocumentMapper;
import com.aiconsultant.consultant.notegrading.protocol.ExpertDomain;
import com.aiconsultant.consultant.pojo.RagIdentityEvaluation;
import com.aiconsultant.consultant.pojo.RagRetrievalHitDTO;
import com.aiconsultant.consultant.service.RagIdentityAccessService;
import com.aiconsultant.consultant.service.RagRerankerService;
import com.aiconsultant.consultant.service.RagRetrievalService;
import com.aiconsultant.consultant.rag.coalesce.RagVectorRecallCoalesceProperties;
import com.aiconsultant.consultant.rag.coalesce.RagVectorRecallCoalesceService;
import com.aiconsultant.consultant.util.RagTextScoring;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Slf4j
@Service
public class RagRetrievalServiceImpl implements RagRetrievalService {

    public static final String METADATA_RAG_IDENTITY_IDS = "rag_identity_ids";

    @Value("${app.rag.retrieval.recall-max-results:50}")
    private int recallMaxResults;
    @Value("${app.rag.retrieval.window-n:1}")
    private int windowN;
    @Value("${app.rag.retrieval.coarse-top-k:24}")
    private int coarseTopK;
    @Value("${app.rag.retrieval.fine-top-k:8}")
    private int fineTopK;
    @Value("${app.rag.retrieval.coarse-summary-weight:0.55}")
    private double coarseSummaryWeight;
    @Value("${app.rag.retrieval.chunk-head-chars:1200}")
    private int chunkHeadChars;
    @Value("${app.rag.rerank.enabled:false}")
    private boolean rerankEnabled;
    @Value("${app.rag.identity-access-enabled:false}")
    private boolean identityAccessEnabled;
    @Value("${app.rag.identity-prefilter-recall-multiplier:2}")
    private int identityPrefilterRecallMultiplier;
    @Value("${app.rag.identity-recall-max-cap:200}")
    private int identityRecallMaxCap;
    @Value("${app.rag.identity-second-search-enabled:true}")
    private boolean identitySecondSearchEnabled;
    @Value("${app.rag.identity-second-search-max-cap:400}")
    private int identitySecondSearchMaxCap;

    @Autowired
    private EmbeddingModel embeddingModel;
    @Autowired
    private EmbeddingStore<TextSegment> redisEmbeddingStore;
    @Autowired
    private RagContentChunkMapper ragContentChunkMapper;
    @Autowired
    private RagDocumentMapper ragDocumentMapper;
    @Autowired
    private RagRerankerService ragRerankerService;
    @Autowired
    private RagIdentityAccessService ragIdentityAccessService;
    @Autowired
    private RagVectorRecallCoalesceService ragVectorRecallCoalesceService;
    @Autowired
    private RagVectorRecallCoalesceProperties ragVectorRecallCoalesceProperties;

    @Override
    public List<RagRetrievalHitDTO> retrieve(String query, Long requestUserId) {
        return retrieve(query, requestUserId, ExpertDomain.GENERAL);
    }

    @Override
    public List<RagRetrievalHitDTO> retrieve(String query, Long requestUserId, ExpertDomain expertScope) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        Embedding queryEmb = null;

        int firstRecall = Math.min(
                Math.max(1, recallMaxResults) * Math.max(1, identityPrefilterRecallMultiplier),
                Math.max(identityRecallMaxCap, recallMaxResults)
        );
        EmbeddingSearchResult<TextSegment> searchResult1;
        if (ragVectorRecallCoalesceProperties.isEnabled()) {
            searchResult1 = ragVectorRecallCoalesceService
                    .recallMono(query, firstRecall, 0.0)
                    .block(Duration.ofSeconds(ragVectorRecallCoalesceProperties.getMonoTimeoutSeconds()));
        } else {
            Response<Embedding> qResp = embeddingModel.embed(query);
            queryEmb = qResp.content();
            EmbeddingSearchRequest req1 = EmbeddingSearchRequest.builder()
                    .queryEmbedding(queryEmb)
                    .maxResults(firstRecall)
                    .minScore(0.0)
                    .build();
            searchResult1 = redisEmbeddingStore.search(req1);
        }
        List<EmbeddingMatch<TextSegment>> summaryMatches = collectSummaryMatchesAfterAclAndIdentity(
                searchResult1,
                requestUserId,
                expertScope
        );

        if (summaryMatches.isEmpty()
                && identityAccessEnabled
                && identitySecondSearchEnabled
                && expertScope != null
                && expertScope != ExpertDomain.GENERAL) {
            if (queryEmb == null) {
                queryEmb = embeddingModel.embed(query).content();
            }
            int secondRecall = Math.min(firstRecall * 2, Math.max(identitySecondSearchMaxCap, firstRecall));
            EmbeddingSearchRequest req2 = EmbeddingSearchRequest.builder()
                    .queryEmbedding(queryEmb)
                    .maxResults(secondRecall)
                    .minScore(0.0)
                    .build();
            EmbeddingSearchResult<TextSegment> searchResult2 = redisEmbeddingStore.search(req2);
            summaryMatches = collectSummaryMatchesAfterAclAndIdentity(
                    searchResult2,
                    requestUserId,
                    expertScope
            );
        }

        // chunkId -> 最佳摘要召回分（多摘要命中同一扩展块时取 max）
        Map<Long, Double> chunkBestSummaryScore = new HashMap<>();
        Map<Long, EmbeddingMatch<TextSegment>> chunkRepresentativeMatch = new HashMap<>();

        for (EmbeddingMatch<TextSegment> m : summaryMatches) {
            TextSegment seg = m.embedded();
            Metadata meta = seg.metadata();
            long summaryId = parseLong(meta.getString("rag_summary_id"), 0);
            long centerChunkId = parseLong(meta.getString("rag_chunk_id"), 0);
            long chapterId = parseLong(meta.getString("rag_chapter_id"), 0);
            long documentId = parseLong(meta.getString("rag_document_id"), 0);
            if (summaryId == 0 || centerChunkId == 0 || chapterId == 0) {
                continue;
            }
            double sScore = m.score() != null ? m.score() : 0;

            List<RagContentChunk> inChapter = ragContentChunkMapper.selectList(
                    new LambdaQueryWrapper<RagContentChunk>()
                            .eq(RagContentChunk::getChapterId, chapterId)
                            .isNull(RagContentChunk::getDeletedAt)
                            .orderByAsc(RagContentChunk::getChunkIndex)
            );
            int centerIdx = -1;
            for (int i = 0; i < inChapter.size(); i++) {
                if (Objects.equals(inChapter.get(i).getId(), centerChunkId)) {
                    centerIdx = i;
                    break;
                }
            }
            if (centerIdx < 0) {
                continue;
            }
            int from = Math.max(0, centerIdx - windowN);
            int to = Math.min(inChapter.size() - 1, centerIdx + windowN);
            for (int i = from; i <= to; i++) {
                RagContentChunk ch = inChapter.get(i);
                Long cid = ch.getId();
                Double prev = chunkBestSummaryScore.get(cid);
                if (prev == null || sScore > prev) {
                    chunkBestSummaryScore.put(cid, sScore);
                    chunkRepresentativeMatch.put(cid, m);
                }
            }
        }

        List<CoarseRow> coarseRows = new ArrayList<>();
        for (Map.Entry<Long, Double> e : chunkBestSummaryScore.entrySet()) {
            Long chunkId = e.getKey();
            RagContentChunk chunk = ragContentChunkMapper.selectById(chunkId);
            if (chunk == null || chunk.getDeletedAt() != null) {
                continue;
            }
            EmbeddingMatch<TextSegment> rep = chunkRepresentativeMatch.get(chunkId);
            if (rep == null) {
                continue;
            }
            Metadata meta = rep.embedded().metadata();
            long summaryId = parseLong(meta.getString("rag_summary_id"), 0);
            long chapterId = parseLong(meta.getString("rag_chapter_id"), 0);
            long documentId = parseLong(meta.getString("rag_document_id"), 0);
            String outlineHead = truncate(rep.embedded().text(), 400);
            String chunkHead = truncate(chunk.getContentText(), chunkHeadChars);
            coarseRows.add(new CoarseRow(chunkId, documentId, chapterId, summaryId, e.getValue(), outlineHead, chunkHead));
        }

        coarseRows.sort(Comparator.comparingDouble((CoarseRow r) -> {
            double jac = RagTextScoring.jaccardCharBigrams(query, r.chunkHead);
            return coarseSummaryWeight * r.summaryScore + (1 - coarseSummaryWeight) * jac;
        }).reversed());

        List<CoarseRow> coarseCut = coarseRows.subList(0, Math.min(coarseTopK, coarseRows.size()));

        if (coarseCut.isEmpty()) {
            return List.of();
        }

        List<RagRetrievalHitDTO> fineList;
        if (rerankEnabled) {
            fineList = rerankWithFallback(query, queryEmb, coarseCut);
        } else {
            fineList = localFineRank(query, queryEmb, coarseCut);
        }
        int to = Math.min(fineTopK, fineList.size());
        List<RagRetrievalHitDTO> cut = new ArrayList<>(fineList.subList(0, to));
        cut = filterByExpertScope(cut, expertScope);
        return ragIdentityAccessService.filterHitsByIdentity(cut, requestUserId, expertScope);
    }

    private List<RagRetrievalHitDTO> filterByExpertScope(List<RagRetrievalHitDTO> hits, ExpertDomain expertScope) {
        if (hits == null || hits.isEmpty()) {
            return hits == null ? List.of() : hits;
        }
        if (expertScope == null || expertScope == ExpertDomain.GENERAL) {
            return hits;
        }
        String need = expertScope.name();
        Set<Long> docIds = new HashSet<>();
        for (RagRetrievalHitDTO h : hits) {
            if (h.getDocumentId() != null) {
                docIds.add(h.getDocumentId());
            }
        }
        if (docIds.isEmpty()) {
            return hits;
        }
        List<RagDocument> docs = ragDocumentMapper.selectBatchIds(docIds);
        Map<Long, String> scopeByDoc = new HashMap<>();
        for (RagDocument d : docs) {
            if (d != null && d.getId() != null) {
                scopeByDoc.put(d.getId(), d.getExpertScope());
            }
        }
        List<RagRetrievalHitDTO> out = new ArrayList<>();
        for (RagRetrievalHitDTO h : hits) {
            Long did = h.getDocumentId();
            if (did == null) {
                continue;
            }
            String docScope = scopeByDoc.get(did);
            if (documentVisibleForExpert(docScope, need)) {
                out.add(h);
            }
        }
        return out;
    }

    private static boolean documentVisibleForExpert(String docExpertScope, String requestedExpert) {
        if (docExpertScope == null || docExpertScope.isBlank()) {
            return true;
        }
        String d = docExpertScope.trim();
        if (d.equalsIgnoreCase(ExpertDomain.GENERAL.name())) {
            return true;
        }
        return d.equalsIgnoreCase(requestedExpert);
    }

    private List<RagRetrievalHitDTO> rerankWithFallback(String query, Embedding queryEmb, List<CoarseRow> coarseCut) {
        List<String> docs = new ArrayList<>();
        for (CoarseRow row : coarseCut) {
            docs.add(truncate(row.chunkHead, 2500));
        }
        Map<Integer, Double> rerankScores = ragRerankerService.rerank(query, docs);
        if (rerankScores == null || rerankScores.isEmpty()) {
            return localFineRank(query, queryEmb, coarseCut);
        }
        List<RagRetrievalHitDTO> ranked = new ArrayList<>();
        for (int i = 0; i < coarseCut.size(); i++) {
            CoarseRow r = coarseCut.get(i);
            double coarseSc = coarseSummaryWeight * r.summaryScore
                    + (1 - coarseSummaryWeight) * RagTextScoring.jaccardCharBigrams(query, r.chunkHead);
            double fine = rerankScores.getOrDefault(i, 0.0);
            ranked.add(RagRetrievalHitDTO.builder()
                    .documentId(r.documentId)
                    .chapterId(r.chapterId)
                    .chunkId(r.chunkId)
                    .summaryId(r.summaryId)
                    .outlineSnippet(r.outlineHead)
                    .chunkTextSnippet(r.chunkHead)
                    .summaryEmbeddingScore(r.summaryScore)
                    .coarseScore(coarseSc)
                    .fineScore(fine)
                    .build());
        }
        ranked.sort(Comparator.comparingDouble(RagRetrievalHitDTO::getFineScore).reversed());
        return ranked;
    }

    private List<RagRetrievalHitDTO> localFineRank(String query, Embedding queryEmb, List<CoarseRow> coarseCut) {
        List<TextSegment> fineSegs = new ArrayList<>();
        for (CoarseRow r : coarseCut) {
            fineSegs.add(TextSegment.from(truncate(r.chunkHead, 2500)));
        }
        Response<List<Embedding>> embResp = embeddingModel.embedAll(fineSegs);
        List<Embedding> chunkEmbs = embResp.content();
        List<RagRetrievalHitDTO> fineList = new ArrayList<>();
        for (int i = 0; i < coarseCut.size(); i++) {
            CoarseRow r = coarseCut.get(i);
            Embedding cEmb = chunkEmbs.get(i);
            double fine = RagTextScoring.cosine(queryEmb, cEmb);
            double coarseSc = coarseSummaryWeight * r.summaryScore
                    + (1 - coarseSummaryWeight) * RagTextScoring.jaccardCharBigrams(query, r.chunkHead);
            fineList.add(RagRetrievalHitDTO.builder()
                    .documentId(r.documentId)
                    .chapterId(r.chapterId)
                    .chunkId(r.chunkId)
                    .summaryId(r.summaryId)
                    .outlineSnippet(r.outlineHead)
                    .chunkTextSnippet(r.chunkHead)
                    .summaryEmbeddingScore(r.summaryScore)
                    .coarseScore(coarseSc)
                    .fineScore(fine)
                    .build());
        }
        fineList.sort(Comparator.comparingDouble(RagRetrievalHitDTO::getFineScore).reversed());
        return fineList;
    }

    /**
     * ACL 通过后，按父文档身份在召回环过滤（不进入粗排）。
     */
    private List<EmbeddingMatch<TextSegment>> collectSummaryMatchesAfterAclAndIdentity(
            EmbeddingSearchResult<TextSegment> searchResult,
            Long requestUserId,
            ExpertDomain expertScope
    ) {
        List<EmbeddingMatch<TextSegment>> aclPass = new ArrayList<>();
        for (EmbeddingMatch<TextSegment> m : searchResult.matches()) {
            TextSegment seg = m.embedded();
            if (seg == null || seg.metadata() == null || !seg.metadata().containsKey("rag_summary_id")) {
                continue;
            }
            long owner = parseLong(seg.metadata().getString("rag_owner_user_id"), -999);
            if (!aclAllowed(requestUserId, owner)) {
                continue;
            }
            aclPass.add(m);
        }
        if (aclPass.isEmpty()) {
            return aclPass;
        }

        Set<Long> candidateDocIds = new HashSet<>();
        for (EmbeddingMatch<TextSegment> m : aclPass) {
            Metadata meta = m.embedded().metadata();
            long documentId = parseLong(meta.getString("rag_document_id"), 0);
            if (documentId > 0) {
                candidateDocIds.add(documentId);
            }
        }
        RagIdentityEvaluation evaluation = ragIdentityAccessService.evaluateForRetrieval(
                candidateDocIds,
                requestUserId,
                expertScope
        );

        List<EmbeddingMatch<TextSegment>> out = new ArrayList<>();
        Set<Long> seenSummaryIds = new HashSet<>();
        for (EmbeddingMatch<TextSegment> m : aclPass) {
            Metadata meta = m.embedded().metadata();
            long documentId = parseLong(meta.getString("rag_document_id"), 0);
            long summaryId = parseLong(meta.getString("rag_summary_id"), 0);
            if (summaryId == 0) {
                continue;
            }
            if (evaluation.gateActive()) {
                if (documentId <= 0 || !evaluation.allowedDocumentIds().contains(documentId)) {
                    continue;
                }
                if (!metadataIdentityConsistent(meta, documentId, evaluation)) {
                    continue;
                }
            }
            if (seenSummaryIds.add(summaryId)) {
                out.add(m);
            }
        }
        return out;
    }

    /**
     * 若向量 metadata 携带 {@link #METADATA_RAG_IDENTITY_IDS}，须与库中 rag_document_identity 一致，否则丢弃并打日志。
     */
    private boolean metadataIdentityConsistent(Metadata meta, long documentId, RagIdentityEvaluation evaluation) {
        if (meta == null || !meta.containsKey(METADATA_RAG_IDENTITY_IDS)) {
            return true;
        }
        String raw = meta.getString(METADATA_RAG_IDENTITY_IDS);
        if (raw == null || raw.isBlank()) {
            return true;
        }
        Set<Long> fromMeta = parseCommaSeparatedLongs(raw);
        if (fromMeta.isEmpty()) {
            return true;
        }
        Set<Long> fromDb = evaluation.identitiesByDocumentId().get(documentId);
        Set<Long> dbSet = fromDb == null ? Set.of() : fromDb;
        if (!fromMeta.equals(dbSet)) {
            log.warn(
                    "rag_identity_ids metadata mismatch documentId={} meta={} db={}",
                    documentId,
                    fromMeta,
                    dbSet
            );
            return false;
        }
        return true;
    }

    private static Set<Long> parseCommaSeparatedLongs(String raw) {
        String[] parts = raw.split(",");
        Set<Long> s = new HashSet<>();
        for (String p : parts) {
            if (p == null) {
                continue;
            }
            String t = p.trim();
            if (t.isEmpty()) {
                continue;
            }
            try {
                s.add(Long.parseLong(t));
            } catch (NumberFormatException ignored) {
                // skip invalid token
            }
        }
        return s;
    }

    private record CoarseRow(long chunkId, long documentId, long chapterId, long summaryId,
                             double summaryScore, String outlineHead, String chunkHead) {
    }

    private static boolean aclAllowed(Long requestUserId, long ownerUserId) {
        if (ownerUserId == -1L) {
            return true;
        }
        if (requestUserId == null) {
            return false;
        }
        return Objects.equals(requestUserId, ownerUserId);
    }

    private static long parseLong(String s, long def) {
        if (s == null || s.isBlank()) {
            return def;
        }
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
