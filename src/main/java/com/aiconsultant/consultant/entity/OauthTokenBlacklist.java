package com.aiconsultant.consultant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("oauth_token_blacklist")
public class OauthTokenBlacklist {

    /**
     * 主键 ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 雪花算法生成的会话 ID (用于和 JWT 载荷对齐)
     */
    private Long tokenId;

    /**
     * 该 Token (通常是 Refresh Token) 原本的物理过期时间
     * 用于定时清理过期无用数据，防止表无限膨胀
     */
    private LocalDateTime expireTime;

    /**
     * 加入黑名单的时间 (系统默认生成)
     */
    private LocalDateTime createTime;
}
