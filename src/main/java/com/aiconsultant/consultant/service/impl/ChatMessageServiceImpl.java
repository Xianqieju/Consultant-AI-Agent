package com.aiconsultant.consultant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aiconsultant.consultant.aiservice.*;
import com.aiconsultant.consultant.config.RabbitMQConfig;
import com.aiconsultant.consultant.entity.ChatMessage;
import com.aiconsultant.consultant.mapper.ChatMessageMapper;
import com.aiconsultant.consultant.pojo.ChatAgentDTO;
import com.aiconsultant.consultant.pojo.ChatMessageDTO;
import com.aiconsultant.consultant.pojo.QuotaOperationDTO;
import com.aiconsultant.consultant.pojo.Result;
import com.aiconsultant.consultant.registry.DynamicToolRegistry;
import com.aiconsultant.consultant.service.ChatAgentService;
import com.aiconsultant.consultant.service.ChatMessageService;
import com.aiconsultant.consultant.service.QuotaService;
import com.aiconsultant.consultant.service.UserSessionService;
import com.aiconsultant.consultant.utils.SessionHolder;
import com.aiconsultant.consultant.utils.SnowflakeIdWorker;
import com.aiconsultant.consultant.utils.UserHolder;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.service.AiServices;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.aiconsultant.consultant.utils.Constants.EXCHANGE_NAME;
import static com.aiconsultant.consultant.utils.Constants.ROUTING_KEY;

@Slf4j
@Service
public class ChatMessageServiceImpl extends ServiceImpl<ChatMessageMapper, ChatMessage> implements ChatMessageService {
    @Autowired
    private ConsultantService consultantService;
    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private ChatMemoryProvider chatMemoryProvider;
    @Autowired
    private ChatAgentService chatAgentService;
    @Autowired
    private SnowflakeIdWorker snowflakeIdWorker;
    @Autowired
    private QuotaService quotaService;
    // 监听指定的队列名称（请确保在 RabbitMQ 配置类中已声明该队列和交换机）
    // 【新增】注入用户会话服务
    @Autowired
    private UserSessionService userSessionService;
    @Autowired
    private IntentRouterService intentRouterService;
    @Autowired
    private TaskAgent taskAgent;
    @Autowired
    private CasualChatAgent casualChatAgent;
    @Autowired
    private ToolRouterService toolRouterService;
    @Autowired
    private DynamicToolRegistry dynamicToolRegistry;
    @Autowired
    private OpenAiStreamingChatModel openAiStreamingChatModel;

    private Flux<String> routeAndExecuteChat(
            Long sessionId, String message, String dynamicPrompt,
            Long txId, Long aiMsgId, Long userId, Integer costAmount) {

        // 1. 动态意图路由：询问大模型，本次对话需要挂载哪些工具？
        List<String> rawTools = toolRouterService.determineRequiredTools(message);
        List<String> requiredTools = rawTools == null ? new ArrayList<>() : rawTools.stream()
                .filter(s -> s != null && !s.isBlank() && !s.equals("[]"))
                .collect(Collectors.toList());
        log.info("会话 [{}], 识别出需要调用的工具列表: {}", sessionId, requiredTools);

        Flux<String> aiResponseFlux;

        // 2. 动态分发与组装策略
        if (requiredTools == null || requiredTools.isEmpty()) {
            // 走轻量级通道：没有任何工具，直接调用静态的闲聊 Agent
            log.info("--> 路由分流：触发【闲聊陪伴智能体】，纯文本流式响应...");
            aiResponseFlux = casualChatAgent.chat(sessionId, message, dynamicPrompt);

        } else {
            // 走重负载通道：根据工具名称，从 Map 中提取真实的 Java 实例
            log.info("--> 路由分流：触发【动态任务智能体】，开始挂载工具链...");
            Object[] toolInstances = dynamicToolRegistry.getToolsByNames(requiredTools);

            // 【核心架构突破】：编程式构建动态 Agent
            // 每次请求到来时，现场组装一个只包含本次所需工具的专属 Agent
            DynamicTaskAgent dynamicAgent = AiServices.builder(DynamicTaskAgent.class)
                    .streamingChatModel(openAiStreamingChatModel) // 绑定流式输出模型
                    .chatMemoryProvider(chatMemoryProvider)       // 绑定历史上下文记忆
                    .tools(toolInstances)                         // 仅注入本次路由选中的工具
                    .build();

            // 发起流式对话
            aiResponseFlux = dynamicAgent.chat(sessionId, message, dynamicPrompt);
        }

        // 3. 统一挂载响应式钩子 (MQ 落库 & TCC 确认/回滚)
        StringBuilder aiContentBuilder = new StringBuilder();

        return aiResponseFlux
                .doOnNext(aiContentBuilder::append)
                .doOnComplete(() -> {
                    String fullAiResponse = aiContentBuilder.toString();
                    log.info("AI 流生成完毕，准备执行 MQ 落库与 TCC 确认。");

                    // 过滤掉思考过程的脏文本（如果存在）
                    String cleanContent = fullAiResponse
                            .replaceAll("(?m)^(思考|行动|观察|Action)：.*$", "")
                            .replaceAll("\\n+", "\n")
                            .trim();

                    // 异步投递【AI回复】到 MQ (落库)
                    ChatMessageDTO aiMsgDTO = new ChatMessageDTO();
                    aiMsgDTO.setId(aiMsgId);
                    aiMsgDTO.setUserId(userId);
                    aiMsgDTO.setSessionId(sessionId);
                    aiMsgDTO.setRole(1);
                    aiMsgDTO.setContent(cleanContent); // 使用清理后的文本
                    aiMsgDTO.setCorrelationId(txId);
                    aiMsgDTO.setStatus(0);
                    rabbitTemplate.convertAndSend(RabbitMQConfig.CHAT_EXCHANGE, RabbitMQConfig.CHAT_ROUTING_KEY, aiMsgDTO);

                    // TCC 阶段二：Confirm (触发异步实扣)
                    QuotaOperationDTO confirmDto = new QuotaOperationDTO();
                    confirmDto.setTxId(txId);
                    confirmDto.setChatId(aiMsgId);
                    confirmDto.setUserId(userId);
                    confirmDto.setAmount(costAmount);
                    rabbitTemplate.convertAndSend("quota.direct", "confirm", confirmDto);
                })
                .doOnError(error -> {
                    log.error("AI 对话生成流异常, 触发额度回滚。userId: {}, txId: {}", userId, txId, error);

                    // TCC 阶段三：Cancel (触发异步回滚)
                    QuotaOperationDTO cancelDto = new QuotaOperationDTO();
                    cancelDto.setTxId(txId);
                    cancelDto.setChatId(aiMsgId);
                    cancelDto.setUserId(userId);
                    cancelDto.setAmount(costAmount);
                    rabbitTemplate.convertAndSend("quota.direct", "cancel", cancelDto);
                });
    }

    @Override
    public Flux<String> streamChatProcess(String message,Long agentId) {
        Long userId = UserHolder.getUser().getId();
        Long txId = snowflakeIdWorker.nextId();       // 流水事务 ID
        Long userMsgId = snowflakeIdWorker.nextId();  // 用户提问的专属消息 ID
        Long aiMsgId = snowflakeIdWorker.nextId();    // AI 回复的专属消息 ID
        Long sessionId = SessionHolder.getSessionId();
        if (sessionId == null) {
            // 拦截器放行了 -1，此处执行真实落库创建
            sessionId = userSessionService.createSession(userId, agentId, message);
            // 回填至上下文，方便本线程后续其他方法可能用到
            SessionHolder.saveSessionId(sessionId);
        }
        Integer costAmount = 1; // 本次对话消耗额度
        // 2. 阶段一：Try (预占额度)
        // 必须在调用大模型前进行同步阻塞检查
        ChatAgentDTO agent = chatAgentService.getAgentById(agentId);
        if(agent==null){
            throw new RuntimeException("智能体不存在");
        }
        boolean trySuccess = quotaService.tryQuota(txId, aiMsgId, userId, costAmount);
        if (!trySuccess) {
            // Try 失败，直接返回错误流，阻断后续的大模型调用
            log.warn("用户额度不足或预占失败，拒绝执行生成。userId: {}", userId);
            return Flux.error(new RuntimeException("额度不足，发送失败"));
        }
        String dynamicPrompt = agent.getSystemPrompt();
        // 1. 异步投递【用户提问】到 MQ
        ChatMessageDTO userMsgDTO = new ChatMessageDTO();
        userMsgDTO.setId(userMsgId);
        userMsgDTO.setUserId(userId);
        userMsgDTO.setSessionId(sessionId);
        userMsgDTO.setRole(0);
        userMsgDTO.setContent(message);
        userMsgDTO.setCorrelationId(txId); // 核心：绑定事务关联 ID
        userMsgDTO.setStatus(0);           // 用户的提问默认是成功的

        rabbitTemplate.convertAndSend(EXCHANGE_NAME, ROUTING_KEY, userMsgDTO);

        // 用于在流传输过程中拼接 AI 的全量回复
        StringBuilder aiContentBuilder = new StringBuilder();
        final Long finalSessionId = sessionId;
        // 2. 调用 LangChain4j 的服务，并挂载生命周期钩子
        return routeAndExecuteChat(
                finalSessionId, message, dynamicPrompt,
                txId, aiMsgId, userId, costAmount
        );
    }

    @Override
    public Result getHistoryAndRefreshMemory() {
        Long userId = UserHolder.getUser().getId();
        Long sessionId = SessionHolder.getSessionId();
        if (sessionId == null) {
            log.info("id为空！");
            return Result.ok(new ArrayList<>());
        }
        log.info("正在查询历史记录，userId: {}, sessionId: {}", userId, sessionId);
        // 1. 从数据库检索该用户在该会话下的所有记录，按时间升序排列
        List<ChatMessage> entities = this.list(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getUserId, userId)
                .eq(ChatMessage::getSessionId, sessionId)
                .orderByAsc(ChatMessage::getCreateTime));

        // 2. 获取并清空当前会话的 AI 记忆
        // memoryId 在这里就是 sessionId
        ChatMemory chatMemory = chatMemoryProvider.get(sessionId);
        chatMemory.clear();

        // 3. 将数据库记录转换为 LangChain4j 的 Message 对象并存入记忆
        List<ChatMessageDTO> dtoList = new ArrayList<>();

        for (ChatMessage entity : entities) {
            // 填充返回给前端的 DTO
            if(entity.getStatus()==0) {
                ChatMessageDTO dto = new ChatMessageDTO(
                        entity.getId(),
                        entity.getUserId(),
                        entity.getSessionId(),
                        entity.getRole(),
                        entity.getContent(),
                        entity.getCorrelationId(),
                        0
                );
                // 同步至 AI 记忆组件
                if (entity.getRole() == 0) { // User
                    chatMemory.add(new UserMessage(entity.getContent()));
                } else if (entity.getRole() == 1) { // Assistant
                    chatMemory.add(new AiMessage(entity.getContent()));
                }
                dtoList.add(dto);
            }
        }
        return Result.ok(dtoList);
    }
}
