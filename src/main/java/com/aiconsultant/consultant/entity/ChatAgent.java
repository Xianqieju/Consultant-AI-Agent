package com.aiconsultant.consultant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("chat_agent")
public class ChatAgent {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;          // 智能体名称，如“高考专业专家”、“心理疏导员”

    private String description;   // 简介，用于前端卡片展示

    private String systemPrompt;  // 核心 System Prompt
}