
package com.aiconsultant.consultant.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("user_session")
public class UserSession {

    @TableId(type = IdType.INPUT)
    private Long id;

    private Long userId;

    private Long agentId;

    private String title;

    @TableLogic(value = "0", delval = "1")
    private Integer isDeleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}