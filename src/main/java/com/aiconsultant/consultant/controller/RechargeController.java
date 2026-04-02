package com.aiconsultant.consultant.controller;

import com.aiconsultant.consultant.pojo.BankCallbackDTO;
import com.aiconsultant.consultant.pojo.PayCallbackDTO;
import com.aiconsultant.consultant.pojo.RechargeRequestDTO;
import com.aiconsultant.consultant.pojo.Result;
import com.aiconsultant.consultant.service.RechargeOrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/recharge")
public class RechargeController {

    @Autowired
    private RechargeOrderService rechargeOrderService;

    /**
     * 第一步：申请充值
     * 内部会调用 BankSimulator，拿到 payToken
     */
    @PostMapping("/apply")
    public Result apply(@RequestBody RechargeRequestDTO dto) {
        return rechargeOrderService.apply(dto);
    }

    /**
     * 第二步：模拟银行回调
     * 银行侧处理完资金后，拿着结果来找我们
     */
    @PostMapping("/callback")
    public String callback(@RequestBody BankCallbackDTO callbackDto) {
        return rechargeOrderService.handleBankCallback(callbackDto);
    }
}
