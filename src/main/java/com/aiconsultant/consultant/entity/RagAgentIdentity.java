package com.aiconsultant.consultant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("rag_agent_identity")
public class RagAgentIdentity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("agent_code")
    private String agentCode;

    @TableField("identity_id")
    private Long identityId;

    private LocalDateTime createdAt;
}
