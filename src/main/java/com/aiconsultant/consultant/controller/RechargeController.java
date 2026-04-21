package com.aiconsultant.consultant.controller;

import com.aiconsultant.consultant.BankSimulator.BankSimulator;
import com.aiconsultant.consultant.pojo.BankCallbackDTO;
import com.aiconsultant.consultant.pojo.OrderNoDTO;
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

    /**
     * 银行模拟：关单（未支付场景），与商户 {@link com.aiconsultant.consultant.service.RechargeOrderService#closeOrder} 不同，此处仅暴露银行侧模拟能力时可选用。
     * 推荐业务关单使用 {@link #closeOrder(OrderNoDTO)}。
     */
    @PostMapping("/bank/sim/close")
    public Result bankSimClose(@RequestBody OrderNoDTO dto) {
        if (dto.getOrderNo() == null || dto.getOrderNo().isEmpty()) {
            return Result.fail("orderNo 不能为空");
        }
        boolean ok = BankSimulator.simulateCloseOrder(dto.getOrderNo());
        return ok ? Result.ok() : Result.fail("银行关单失败：无此单、已支付或已处理");
    }

    /**
     * 银行模拟：对已支付流水退款（联调/补偿）。
     */
    @PostMapping("/bank/sim/refund")
    public Result bankSimRefund(@RequestBody OrderNoDTO dto) {
        return rechargeOrderService.simulateBankRefund(dto.getOrderNo());
    }

    /**
     * 商户侧关单：校验归属 + 乐观锁更新为已关单，并调用银行关单模拟。
     */
    @PostMapping("/order/close")
    public Result closeOrder(@RequestBody OrderNoDTO dto) {
        if (dto.getOrderNo() == null || dto.getOrderNo().isEmpty()) {
            return Result.fail("orderNo 不能为空");
        }
        return rechargeOrderService.closeOrder(dto.getOrderNo());
    }
}
