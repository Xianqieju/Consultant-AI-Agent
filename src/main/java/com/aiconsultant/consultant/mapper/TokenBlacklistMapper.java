package com.aiconsultant.consultant.mapper;

import com.aiconsultant.consultant.entity.OauthTokenBlacklist;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TokenBlacklistMapper extends BaseMapper<OauthTokenBlacklist> {
}
