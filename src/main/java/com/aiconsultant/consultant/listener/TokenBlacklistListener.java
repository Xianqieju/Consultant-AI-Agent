package com.aiconsultant.consultant.listener;

import com.aiconsultant.consultant.manager.LocalTokenBlacklistManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class TokenBlacklistListener {

    @Autowired
    private LocalTokenBlacklistManager localTokenBlacklistManager;

    /**
     * 监听广播。@Queue 不指定名字，Spring 会自动生成一个类似 amq.gen-xxx 的临时队列，
     * 断开连接后自动删除，完美契合多实例广播场景。
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(),
            exchange = @Exchange(value = "token.blacklist.fanout", type = ExchangeTypes.FANOUT)
    ))
    public void receiveBlacklistBroadcast(Long tokenId) {
        if (tokenId != null) {
            localTokenBlacklistManager.addBlacklist(tokenId);
            log.info("节点接收到广播，TokenId: {} 已同步至本地咆哮位图黑名单", tokenId);
        }
    }
}
