package com.aiconsultant.consultant.mapper;

import com.aiconsultant.consultant.entity.MqIdempotent;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MqIdempotentMapper extends BaseMapper<MqIdempotent> {}
