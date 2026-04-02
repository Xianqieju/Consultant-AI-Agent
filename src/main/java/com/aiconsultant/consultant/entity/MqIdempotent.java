package com.aiconsultant.consultant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("mq_idempotent")
public class MqIdempotent {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String bizId;
    private String consumerName;
}