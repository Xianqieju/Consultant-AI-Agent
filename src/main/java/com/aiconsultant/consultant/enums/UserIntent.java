package com.aiconsultant.consultant.enums;
public enum UserIntent {
    /**
     * 笔记记录意图（需要调用天气、数据库等繁重工具）
     */
    NOTE_TAKING,

    /**
     * 日常闲聊/心理安抚意图（只需纯文本生成，速度快，不消耗工具成本）
     */
    CASUAL_CHAT
}
