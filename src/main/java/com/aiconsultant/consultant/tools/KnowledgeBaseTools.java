package com.aiconsultant.consultant.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class KnowledgeBaseTools implements BaseAgentTool{

    @Autowired
    private ContentRetriever contentRetriever;

    // 严谨的描述词是防止 AI 乱搜的关键
    @Tool("查询高考分数线、志愿填报政策、大学专业介绍或心理学客观理论。仅当用户明确询问具体专业知识时才可调用此工具。日常闲聊或情绪安抚时禁止调用。")
    public String searchProfessionalKnowledge(
            @P("提取出的核心搜索关键词，例如'计算机科学与技术专业介绍'或'抑郁倾向缓解机制'") String keyword) {

        log.info("Agent 决定检索知识库，搜索关键词: [{}]", keyword);

        try {
            // 1. 构造检索查询
            Query query = Query.from(keyword);

            // 2. 执行向量检索
            List<Content> contents = contentRetriever.retrieve(query);

            // 3. 结果判空处理
            if (contents == null || contents.isEmpty()) {
                log.info("未检索到相关知识");
                return "知识库中未找到与此相关的具体资料。请基于你的通用常识回答，或询问用户是否需要换个说法。";
            }

            // 4. 将检索到的 TextSegment 拼接为字符串返回给大模型
            StringBuilder resultBuilder = new StringBuilder("以下是检索到的相关知识内容：\n");
            for (int i = 0; i < contents.size(); i++) {
                resultBuilder.append(i + 1).append(". ")
                        .append(contents.get(i).textSegment().text())
                        .append("\n");
            }

            return resultBuilder.toString();

        } catch (Exception e) {
            log.error("知识库检索执行异常", e);
            return "检索系统发生异常，请告知用户暂时无法查询该数据。";
        }
    }
}
