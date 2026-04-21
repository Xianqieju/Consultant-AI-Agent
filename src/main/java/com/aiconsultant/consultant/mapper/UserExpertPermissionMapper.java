package com.aiconsultant.consultant.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserExpertPermissionMapper {

    @Select("""
            SELECT COUNT(1) FROM user_expert_permission
            WHERE user_id = #{userId} AND expert_code = #{expertCode} AND allowed = 1
            """)
    long countAllowed(@Param("userId") Long userId, @Param("expertCode") String expertCode);
}
