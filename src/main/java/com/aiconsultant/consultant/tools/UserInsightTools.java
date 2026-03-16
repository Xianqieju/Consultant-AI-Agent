package com.aiconsultant.consultant.tools;

import com.aiconsultant.consultant.entity.UserSession;
import com.aiconsultant.consultant.service.UserNoteService;
import com.aiconsultant.consultant.service.UserSessionService;
import com.aiconsultant.consultant.utils.UserHolder;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component("userInsightTools")
public class UserInsightTools implements BaseAgentTool{

    @Autowired
    private UserNoteService userNoteService;
    @Autowired
    private UserSessionService userSessionService;

    /**
     * Tool 1: 记忆检索
     */
    @Tool("当用户询问过去的决定、历史倾向，或者你需要结合用户之前的心理状态/专业偏好时，调用此工具检索历史笔记。")
    public String searchUserNotes(@P("需要检索的核心关键词，例如'计算机专业'、'焦虑'、'性格测试'") String keyword) {
        // 1. 得益于你之前的 TaskDecorator，这里可以直接拿到 UserId！
        Long userId = UserHolder.getUser().getId();
        log.info("Agent 触发检索 Tool, userId: {}, keyword: {}", userId, keyword);

        // 2. 真实业务：去数据库或 ES/向量数据库 中模糊查询该用户的笔记
        // String result = userNoteService.search(userId, keyword);

        // 模拟返回结果给大模型
        return "检索到相关记录：用户曾在上个月表示对人工智能专业感兴趣，但担心数学成绩不好。";
    }

    /**
     * Tool 2: 知识沉淀与上传
     */

    @Tool("当你为用户做出了重要的阶段性总结、深度的心理分析，或规划了具体的行动清单后，调用此工具将这些高价值信息保存为结构化笔记。")
    public String saveUserNote(
            @P("笔记的标题，要求简明扼要，不超过15个字") String title,
            @P("笔记的正文内容，使用 Markdown 格式排版") String content,
            @P("笔记分类标签，例如'心理辅导'、'志愿规划'、'备考方法'") String tag,
            @ToolMemoryId Long sessionId // 【关键点】：让 Agent 把会话 ID 带过来
            ) {

        // 从我们之前配置的 TaskDecorator 透传的上下文中获取用户 ID
        // Long userId = UserHolder.getUser().getId();

        log.info("sessonId:"+sessionId);
        UserSession session = userSessionService.lambdaQuery()
                .eq(UserSession::getId, sessionId) // 补上第二个参数 sessionId
                .one();
        if (session == null) {
            // 处理查不到会话的情况，提前 return
            return "系统提示：会话已失效，无法保存笔记。";
        }
        Long userId = session.getUserId();
        log.info("Agent 触发保存 Tool, userId: {}, title: {}, tag: {}", userId, title, tag);

        try {
            // 调用之前铺垫好的 Service 方法
            boolean success = userNoteService.saveNoteFromAgent(userId, title, content, tag);

            if (success) {
                // 成功时的观察结果 (Observation)
                return "系统提示：笔记已成功保存至用户的个人档案库。你可以用温暖的语气告诉用户已经帮TA记录下来了。";
            } else {
                // 失败时的观察结果
                return "系统提示：笔记保存失败，数据库写入未受影响。请委婉地告诉用户系统暂时打了个盹，稍后再试。";
            }
        } catch (Exception e) {
            log.error("保存笔记工具执行异常: ", e);
            // 异常兜底，防止 Agent 崩溃链断裂
            return "系统提示：保存过程发生后端系统错误。请向用户致歉，说明暂时无法记录。";
        }
    }
}
