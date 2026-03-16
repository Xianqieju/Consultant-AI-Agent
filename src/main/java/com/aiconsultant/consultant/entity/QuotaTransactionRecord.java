package com.aiconsultant.consultant.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("quota_transaction_record")
public class QuotaTransactionRecord {
    @TableId(type = IdType.INPUT)
    private Long txId;

    private Long chatId;

    private Long userId;

    private Integer amount;
    /**
     * 状态: 0-TRY, 1-CONFIRM, 2-CANCEL
     */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
