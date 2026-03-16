package com.aiconsultant.consultant.registry;

import com.aiconsultant.consultant.tools.BaseAgentTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class DynamicToolRegistry {

    // 核心：Spring 会自动收集所有注册的 Bean。
    // 只要你的 Tool 类上加了 @Component("weatherTools")，就会自动被塞进这个 Map
    @Autowired
    private Map<String, BaseAgentTool> applicationContextBeans;

    /**
     * 根据路由服务返回的 Bean Name 列表，动态提取实例
     */
    public Object[] getToolsByNames(List<String> requiredToolNames) {
        if (requiredToolNames == null || requiredToolNames.isEmpty()) {
            return new Object[0];
        }

        List<Object> activeTools = new ArrayList<>();
        for (String toolName : requiredToolNames) {
            Object toolInstance = applicationContextBeans.get(toolName);
            if (toolInstance != null) {
                // 仅当提取出的 Bean 确实包含了 @Tool 注解的方法时，才认为是合法工具
                // (LangChain4j 底层会做校验，这里简单组装即可)
                activeTools.add(toolInstance);
            } else {
                log.warn("大模型路由请求了未知的工具名称: [{}]", toolName);
            }
        }

        log.info("本次会话动态挂载的工具数量: {}", activeTools.size());
        return activeTools.toArray();
    }
}