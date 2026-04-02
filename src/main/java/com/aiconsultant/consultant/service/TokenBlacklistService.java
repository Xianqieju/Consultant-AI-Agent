package com.aiconsultant.consultant.service;

import com.aiconsultant.consultant.entity.OauthTokenBlacklist;
import com.aiconsultant.consultant.mapper.TokenBlacklistMapper;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.stereotype.Service;

@Service
public interface TokenBlacklistService extends IService<OauthTokenBlacklist> {
}
