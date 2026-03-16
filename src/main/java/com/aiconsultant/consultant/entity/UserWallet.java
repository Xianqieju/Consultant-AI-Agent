package com.aiconsultant.consultant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;


@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("user_wallet")
public class UserWallet {
    @TableId(type = IdType.INPUT)
    private long id;
    private int quota;
    private int frozenQuota;
    private int version;
    private LocalDateTime updateTime;
}
