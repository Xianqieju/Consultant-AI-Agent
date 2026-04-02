package com.aiconsultant.consultant.controller;

import com.aiconsultant.consultant.annotation.RateLimit;
import com.aiconsultant.consultant.manager.ReactivePermitManager;
import com.aiconsultant.consultant.pojo.ChatAgentDTO;
import com.aiconsultant.consultant.pojo.ChatRequestDTO;
import com.aiconsultant.consultant.pojo.Result;
import com.aiconsultant.consultant.pojo.UserDTO;
import com.aiconsultant.consultant.service.ChatAgentService;
import com.aiconsultant.consultant.service.ChatMessageService;
import com.aiconsultant.consultant.utils.UserHolder;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.concurrent.Executor;

@RestController
@RequestMapping("/chat") // 统一给整个类加上 /chat 前缀，更符合 RESTful 规范
@Slf4j
public class ChatController {

    @Autowired
    private ChatMessageService chatMessageService;
    @Autowired
    private ChatAgentService chatAgentService;
    @Resource(name = "aiExecutor")
    private Executor aiExecutor;
    @Autowired
    private ReactivePermitManager reactivePermitManager;
    /**
     * 流式对话接口
     * 改为 POST，避免长文本导致的 URL 长度超限
     */
    @PostMapping(value = "/process", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    // @RateLimit 依然可以保留，用于限制单用户的恶意高频请求
    public Flux<String> chat(@RequestBody ChatRequestDTO requestDTO) {

        // 1. 【前置操作】：所有请求一进来，立刻、优先去获取智能体信息
        // 借助底层的航班机制，这里的万级并发会被自动合并为单次 DB 查询
        UserDTO userDTO = UserHolder.getUser();
        return chatAgentService.getAgentById(requestDTO.getAgentId())
                .publishOn(Schedulers.boundedElastic())
                .flatMapMany(agent -> {
                    log.info("智能体 [{}] 信息获取完毕，进入队列申请 AI 执行许可证...", agent.getName());

                    // 组装参数
                    ChatAgentDTO chatAgentDTO = new ChatAgentDTO();
                    chatAgentDTO.setId(agent.getId());
                    chatAgentDTO.setName(agent.getName());
                    chatAgentDTO.setSystemPrompt(agent.getSystemPrompt());

                    // 2. 【排队摇号】：带着已经组装好的参数，去申请入场券
                    return reactivePermitManager.acquire()
                            // 3. 【拿到票后】：直接切入真正的对话流
                            // 使用 thenMany，因为 acquire 返回 Mono<Void>，完成后接着执行 Flux
                            .timeout(Duration.ofSeconds(60))
                            .thenMany(
                                    chatMessageService.streamChatProcess(
                                            requestDTO.getMessage(),
                                            requestDTO.getAgentId(),
                                            chatAgentDTO,
                                            userDTO
                                    ).subscribeOn(Schedulers.fromExecutor(aiExecutor))
                            )
                            // 4. 【最后关卡】：释放名额。注意，doFinally 必须挂载在 acquire 之后的链条上
                            .doFinally(signalType -> {
                                log.info("对话流因 {} 终止，归还执行许可证释放资源", signalType);
                                reactivePermitManager.release();
                            });
                });
    }

    /**
     * 获取历史记录
     */
    @GetMapping("/memory")
    public Result getChat() {
        return chatMessageService.getHistoryAndRefreshMemory();
    }

    /**
     * 针对这块，如果你之前没给 controller 加前缀，
     * 注意调整访问路径，或者把类上的 @RequestMapping("/chat") 拿掉。
     */
    @GetMapping("/agentInfo")
    public Result getAgentInfo() {
        return chatAgentService.getAgentDescriptionList()
                .timeout(Duration.ofSeconds(16)) // 稍微多给 1 秒，覆盖 Service 的 15 秒超时
                .onErrorReturn(Result.fail("系统繁忙，请稍后再试")) // 最后的兜底
                .block();
    }
}
