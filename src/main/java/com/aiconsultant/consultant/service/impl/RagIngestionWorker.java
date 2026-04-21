package com.aiconsultant.consultant.service.impl;

import cn.hutool.core.io.FileUtil;
import com.aiconsultant.consultant.aiservice.rag.RagOutlineAgent;
import com.aiconsultant.consultant.entity.RagContentChunk;
import com.aiconsultant.consultant.entity.RagDocument;
import com.aiconsultant.consultant.entity.RagDocumentChapter;
import com.aiconsultant.consultant.entity.RagDocumentIdentity;
import com.aiconsultant.consultant.entity.RagSummaryOutline;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.aiconsultant.consultant.mapper.RagContentChunkMapper;
import com.aiconsultant.consultant.mapper.RagDocumentIdentityMapper;
import com.aiconsultant.consultant.mapper.RagDocumentChapterMapper;
import com.aiconsultant.consultant.mapper.RagSummaryOutlineMapper;
import com.aiconsultant.consultant.service.RagDocumentService;
import com.aiconsultant.consultant.service.RagSummaryOutlineService;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * 异步：解析本地文件 → 章节/段落块 → 调用大模型生成摘要提纲 → 入库并激活当前版本。
 */
@Slf4j
@Component
public class RagIngestionWorker {

    private static final int MAX_CHUNK_CHARS = 4000;
    private static final int LLM_INPUT_MAX = 12000;

    @Value("${app.rag.identity-metadata-max-len:512}")
    private int identityMetadataMaxLen;

    @Autowired
    private RagDocumentService ragDocumentService;
    @Autowired
    private RagDocumentChapterMapper ragDocumentChapterMapper;
    @Autowired
    private RagContentChunkMapper ragContentChunkMapper;
    @Autowired
    private RagSummaryOutlineMapper ragSummaryOutlineMapper;
    @Autowired
    private RagSummaryOutlineService ragSummaryOutlineService;
    @Autowired
    private RagOutlineAgent ragOutlineAgent;
    @Autowired
    private EmbeddingModel embeddingModel;
    @Autowired
    private EmbeddingStore<TextSegment> redisEmbeddingStore;
    @Autowired
    private RagDocumentIdentityMapper ragDocumentIdentityMapper;

    @Async
    public void runIngestAsync(Long documentId, String absolutePath, String fileName, String fileSuffix) {
        try {
            RagDocument docRow = ragDocumentService.getById(documentId);
            long ownerUserId = docRow != null && docRow.getUserId() != null ? docRow.getUserId() : -1L;

            String fullText = loadPlainText(absolutePath, fileSuffix);
            if (fullText == null || fullText.isBlank()) {
                failDocument(documentId, "解析结果为空");
                return;
            }

            List<ChapterPart> chapters = splitChapters(fullText);
            int chapterOrder = 0;
            for (ChapterPart ch : chapters) {
                RagDocumentChapter chapterRow = new RagDocumentChapter()
                        .setDocumentId(documentId)
                        .setParentChapterId(null)
                        .setChapterNo(String.valueOf(chapterOrder + 1))
                        .setChapterTitle(ch.title())
                        .setLevel(1)
                        .setSortOrder(chapterOrder)
                        .setApproxTokenCount(ch.body().length())
                        .setVersion(1)
                        .setIsAvailable(1)
                        .setCreatedAt(LocalDateTime.now())
                        .setUpdatedAt(LocalDateTime.now());
                ragDocumentChapterMapper.insert(chapterRow);

                List<String> chunkTexts = splitParagraphChunks(ch.body(), MAX_CHUNK_CHARS);
                int chunkIdx = 0;
                for (String chunkText : chunkTexts) {
                    RagContentChunk chunkRow = new RagContentChunk()
                            .setDocumentId(documentId)
                            .setChapterId(chapterRow.getId())
                            .setChunkIndex(chunkIdx)
                            .setChunkType("PARAGRAPH")
                            .setContentText(chunkText)
                            .setTokenCount(chunkText.length())
                            .setVersion(1)
                            .setIsAvailable(1)
                            .setCreatedAt(LocalDateTime.now())
                            .setUpdatedAt(LocalDateTime.now());
                    ragContentChunkMapper.insert(chunkRow);

                    String forLlm = chunkText.length() > LLM_INPUT_MAX
                            ? chunkText.substring(0, LLM_INPUT_MAX) + "\n\n...(已截断用于摘要)"
                            : chunkText;
                    String outline;
                    try {
                        outline = ragOutlineAgent.outlineChunk(forLlm);
                    } catch (Exception ex) {
                        log.warn("摘要提纲生成失败 chunkId pending, documentId={}", documentId, ex);
                        outline = "[摘要生成失败] " + ex.getMessage();
                    }

                    RagSummaryOutline summary = new RagSummaryOutline()
                            .setDocumentId(documentId)
                            .setChapterId(chapterRow.getId())
                            .setChunkId(chunkRow.getId())
                            .setOutlineText(outline)
                            .setOutlineFormat("MARKDOWN")
                            .setEmbeddingModel("")
                            .setVectorStatus(0)
                            .setVectorRef("")
                            .setBm25IndexKey("")
                            .setVersion(1)
                            .setIsAvailable(0)
                            .setRegenReason("ingest_v1")
                            .setCreatedAt(LocalDateTime.now())
                            .setUpdatedAt(LocalDateTime.now());
                    ragSummaryOutlineMapper.insert(summary);
                    ragSummaryOutlineService.activateSummary(summary.getId());
                    indexSummaryVector(documentId, chapterRow.getId(), chunkRow.getId(), summary, outline, ownerUserId);
                    chunkIdx++;
                }
                chapterOrder++;
            }

            ragDocumentService.lambdaUpdate()
                    .eq(RagDocument::getId, documentId)
                    .set(RagDocument::getParseStatus, 1)
                    .set(RagDocument::getUpdatedAt, LocalDateTime.now())
                    .update();
            log.info("RAG 入库完成 documentId={}", documentId);
        } catch (Exception e) {
            log.error("RAG 入库失败 documentId={}", documentId, e);
            failDocument(documentId, e.getMessage());
        }
    }

    private void indexSummaryVector(
            Long documentId,
            Long chapterId,
            Long chunkId,
            RagSummaryOutline summary,
            String outline,
            long ownerUserId
    ) {
        Metadata metadata = new Metadata();
        metadata.put("rag_summary_id", String.valueOf(summary.getId()));
        metadata.put("rag_chunk_id", String.valueOf(chunkId));
        metadata.put("rag_chapter_id", String.valueOf(chapterId));
        metadata.put("rag_document_id", String.valueOf(documentId));
        metadata.put("rag_owner_user_id", String.valueOf(ownerUserId));
        String identityCsv = buildRagIdentityIdsMetadata(documentId, identityMetadataMaxLen);
        if (identityCsv != null && !identityCsv.isEmpty()) {
            metadata.put(RagRetrievalServiceImpl.METADATA_RAG_IDENTITY_IDS, identityCsv);
        }
        TextSegment seg = TextSegment.from(outline, metadata);
        try {
            Response<Embedding> resp = embeddingModel.embed(seg);
            Embedding emb = resp.content();
            String embeddingId = redisEmbeddingStore.add(emb, seg);
            summary.setVectorRef(embeddingId != null ? embeddingId : "");
            summary.setVectorStatus(1);
            summary.setEmbeddingModel("embedding-model");
            ragSummaryOutlineMapper.updateById(summary);
        } catch (Exception ex) {
            log.warn("摘要向量写入 Redis 失败 summaryId={}", summary.getId(), ex);
            summary.setVectorStatus(2);
            ragSummaryOutlineMapper.updateById(summary);
        }
    }

    private String buildRagIdentityIdsMetadata(Long documentId, int maxLen) {
        if (documentId == null || maxLen <= 0) {
            return "";
        }
        List<RagDocumentIdentity> rows = ragDocumentIdentityMapper.selectList(
                new LambdaQueryWrapper<RagDocumentIdentity>()
                        .eq(RagDocumentIdentity::getDocumentId, documentId)
        );
        if (rows == null || rows.isEmpty()) {
            return "";
        }
        List<Long> ids = rows.stream()
                .map(RagDocumentIdentity::getIdentityId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.toList());
        String joined = ids.stream().map(String::valueOf).collect(Collectors.joining(","));
        if (joined.length() <= maxLen) {
            return joined;
        }
        String truncated = joined.substring(0, maxLen);
        int lastComma = truncated.lastIndexOf(',');
        if (lastComma > 0) {
            truncated = truncated.substring(0, lastComma);
        }
        log.warn("rag_identity_ids 超过上限已截断 documentId={} maxLen={}", documentId, maxLen);
        return truncated;
    }

    private void failDocument(Long documentId, String reason) {
        ragDocumentService.lambdaUpdate()
                .eq(RagDocument::getId, documentId)
                .set(RagDocument::getParseStatus, 2)
                .set(RagDocument::getUpdatedAt, LocalDateTime.now())
                .update();
        log.warn("RAG 标记失败 documentId={}, reason={}", documentId, reason);
    }

    private String loadPlainText(String absolutePath, String suffix) throws IOException {
        if (absolutePath == null) {
            return "";
        }
        File file = new File(absolutePath);
        if (!file.isFile()) {
            return "";
        }
        String ext = suffix != null ? suffix.toLowerCase(Locale.ROOT) : FileUtil.extName(file.getName()).toLowerCase(Locale.ROOT);
        if ("pdf".equals(ext)) {
            ApachePdfBoxDocumentParser parser = new ApachePdfBoxDocumentParser();
            Document doc = parser.parse(new FileInputStream(file));
            return doc.text();
        }
        if ("txt".equals(ext) || "md".equals(ext)) {
            return FileUtil.readString(file, StandardCharsets.UTF_8);
        }
        log.warn("暂不支持的文件类型，按 UTF-8 文本尝试读取: {}", ext);
        return FileUtil.readString(file, StandardCharsets.UTF_8);
    }

    private List<ChapterPart> splitChapters(String full) {
        String text = full.trim();
        List<ChapterPart> list = new ArrayList<>();
        String[] parts = text.split("(?=\\n[\\s　]*第[0-9一二三四五六七八九十百千]+章)");
        if (parts.length <= 1) {
            list.add(new ChapterPart("全文", text));
            return list;
        }
        int i = 0;
        for (String p : parts) {
            String t = p.trim();
            if (t.isEmpty()) {
                continue;
            }
            String firstLine = t.lines().findFirst().orElse("章节" + i);
            String title = firstLine.length() > 120 ? firstLine.substring(0, 120) : firstLine;
            list.add(new ChapterPart(title, t));
            i++;
        }
        if (list.isEmpty()) {
            list.add(new ChapterPart("全文", text));
        }
        return list;
    }

    private List<String> splitParagraphChunks(String chapterText, int maxChars) {
        String t = chapterText.trim();
        if (t.length() <= maxChars) {
            return List.of(t);
        }
        List<String> out = new ArrayList<>();
        String[] paras = t.split("\\n\\s*\\n");
        StringBuilder cur = new StringBuilder();
        for (String p : paras) {
            if (cur.length() + p.length() + 2 > maxChars && cur.length() > 0) {
                out.add(cur.toString());
                cur = new StringBuilder();
            }
            if (cur.length() > 0) {
                cur.append("\n\n");
            }
            cur.append(p);
        }
        if (cur.length() > 0) {
            out.add(cur.toString());
        }
        if (out.isEmpty()) {
            out.add(t.substring(0, Math.min(t.length(), maxChars)));
        }
        return out;
    }

    private record ChapterPart(String title, String body) {
    }
}
