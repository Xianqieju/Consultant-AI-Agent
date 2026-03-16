package com.aiconsultant.consultant.controller;

import com.aiconsultant.consultant.annotation.RateLimit;
import com.aiconsultant.consultant.pojo.ChatRequestDTO;
import com.aiconsultant.consultant.pojo.Result;
import com.aiconsultant.consultant.service.ChatAgentService;
import com.aiconsultant.consultant.service.ChatMessageService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

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
    /**
     * 流式对话接口
     * 改为 POST，避免长文本导致的 URL 长度超限
     */
    @PostMapping(value = "/process", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RateLimit
    public Flux<String> chat(@RequestBody ChatRequestDTO requestDTO) {

        // Tomcat 线程执行到这里，准备移交工作

        return chatMessageService.streamChatProcess(requestDTO.getMessage(), requestDTO.getAgentId())
                // 【核心大活】：强制将整个流的执行逻辑转移到 aiExecutor 线程池
                .subscribeOn(Schedulers.fromExecutor(aiExecutor))
                // 可选：打个日志观察 Tomcat 线程是否真的跑掉了
                .doOnSubscribe(subscription ->
                        log.info("连接已挂起，Tomcat 线程释放，任务交由 {} 处理", Thread.currentThread().getName())
                );
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
        return chatAgentService.getAgentDescriptionList();
    }
}
