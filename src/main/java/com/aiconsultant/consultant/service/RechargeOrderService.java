package com.aiconsultant.consultant.service;

import com.aiconsultant.consultant.entity.RechargeOrder;
import com.aiconsultant.consultant.mapper.RechargeOrderMapper;
import com.aiconsultant.consultant.pojo.BankCallbackDTO;
import com.aiconsultant.consultant.pojo.RechargeRequestDTO;
import com.aiconsultant.consultant.pojo.Result;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.stereotype.Service;

@Service
public interface RechargeOrderService extends IService<RechargeOrder> {
    Result apply(RechargeRequestDTO dto);

    String handleBankCallback(BankCallbackDTO callbackDto);
}
