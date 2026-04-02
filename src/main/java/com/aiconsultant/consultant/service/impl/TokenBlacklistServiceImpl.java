package com.aiconsultant.consultant.service.impl;

import com.aiconsultant.consultant.entity.OauthTokenBlacklist;
import com.aiconsultant.consultant.mapper.TokenBlacklistMapper;
import com.aiconsultant.consultant.service.TokenBlacklistService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class TokenBlacklistServiceImpl extends ServiceImpl<TokenBlacklistMapper,OauthTokenBlacklist> implements TokenBlacklistService {
}
