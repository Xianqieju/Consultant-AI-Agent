package com.aiconsultant.consultant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.Version;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("recharge_order")
public class RechargeOrder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String orderNo;
    private BigDecimal amount;
    /** 0:待支付 1:成功 2:失败 3:已关单(取消) */
    private Integer status;
    /** 乐观锁版本（需表字段 version，默认 0） */
    @Version
    private Integer version;

    /** 创建时间（用于超时扫描；需库表有 create_time） */
    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
