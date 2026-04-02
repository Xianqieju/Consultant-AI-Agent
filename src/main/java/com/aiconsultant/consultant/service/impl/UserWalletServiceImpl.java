package com.aiconsultant.consultant.service.impl;

import com.aiconsultant.consultant.entity.UserWallet;
import com.aiconsultant.consultant.mapper.UserWalletMapper;
import com.aiconsultant.consultant.service.UserWalletService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class UserWalletServiceImpl extends ServiceImpl<UserWalletMapper, UserWallet> implements UserWalletService {
}
