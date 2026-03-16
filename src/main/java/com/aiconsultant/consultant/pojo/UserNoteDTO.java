package com.aiconsultant.consultant.pojo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class UserNoteDTO {
    // 解决雪花ID传给前端精度丢失的问题
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    private String title;

    private String content;

    private String tag;

    private LocalDateTime createTime;
}