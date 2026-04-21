package com.aiconsultant.consultant.tools;

import com.aiconsultant.consultant.pojo.RagRetrievalHitDTO;
import com.aiconsultant.consultant.service.RagRetrievalService;
import com.aiconsultant.consultant.utils.UserHolder;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 面试笔记老师：RAG 检索（摘要向量召回 → 章内窗口扩展 → 粗排 → 精排）。
 */
@Slf4j
@Component("interviewRagTools")
public class InterviewRagTools implements BaseAgentTool {

    @Autowired
    private RagRetrievalService ragRetrievalService;

    @Tool("当需要结合用户知识库中的教材/讲义/笔记要点回答面试相关问题时调用。传入检索查询语句。")
    public String searchInterviewKnowledgeBase(
            @P("面向知识库的检索查询，例如：'Java 并发 JMM 面试'") String query
    ) {
        log.info("[InterviewRAG] 检索 query={}", query);
        if (UserHolder.getUser() == null) {
            return "系统提示：未登录，无法检索个人知识库。";
        }
        Long uid = UserHolder.getUser().getId();
        List<RagRetrievalHitDTO> hits = ragRetrievalService.retrieve(query, uid);
        if (hits.isEmpty()) {
            return "【检索结果】未找到与查询高度相关的知识库片段。请基于通用知识回答，并说明依据不足。";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("【检索结果】以下为摘要命中后经粗排/精排后的父文档片段（含 documentId/chunkId 便于引用）：\n");
        int i = 1;
        for (RagRetrievalHitDTO h : hits) {
            sb.append(i++).append(") doc=").append(h.getDocumentId())
                    .append(" chapter=").append(h.getChapterId())
                    .append(" chunk=").append(h.getChunkId())
                    .append(" summary=").append(h.getSummaryId())
                    .append(" fine=").append(String.format("%.4f", h.getFineScore()))
                    .append("\n提纲摘要: ").append(h.getOutlineSnippet()).append("\n原文片段: ")
                    .append(h.getChunkTextSnippet()).append("\n---\n");
        }
        return sb.toString();
    }
}
