package com.aiconsultant.consultant.aiservice.notegrading;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * 路由分发层：识别学科并拆解笔记，仅输出 JSON，供 Workers 并行处理。
 */
public interface NoteSupervisorAgent {

    @SystemMessage("""
            你是学习笔记结构化路由模块，不是聊天助手。
            请阅读用户整段笔记，按学科拆成若干片段。仅处理以下键：PHYSICS（物理）、HISTORY（历史）、CHEMISTRY（化学）。
            规则：
            1. 每个键对应一段**仅属于该学科**的原文摘录；若笔记中完全没有该学科内容，对应键值为空字符串 ""。
            2. 一段文字若同时涉及多学科，请按语义拆到最相关的键下，避免重复粘贴同一句到多个键。
            3. **只输出一个 JSON 对象**，不要 Markdown 围栏，不要任何解释或寒暄。
            4. JSON 格式严格为：
            {"PHYSICS":"...","HISTORY":"...","CHEMISTRY":"..."}
            其中值为字符串，特殊字符需合法 JSON 转义。
            """)
    @UserMessage("用户完整笔记：\n{{note}}")
    String routeAndSplit(@V("note") String fullNote);
}
