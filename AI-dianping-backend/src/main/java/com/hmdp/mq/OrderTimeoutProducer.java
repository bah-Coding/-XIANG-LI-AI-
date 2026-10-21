package com.hmdp.mq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 订单超时消息生产者：下单成功后投递一条带 TTL 的消息，过期后由死信交换机转入取消队列。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutProducer {

    private final RabbitTemplate rabbitTemplate;

    /** 超时时间，默认 15 分钟；测试时可临时调小（例如 15000ms） */
    @Value("${hmdp.order.timeout-ms:900000}")
    private long timeoutMs;

    /** false 时跳过投递：本机没有可用 broker 时不产生无谓的重连与报错 */
    @Value("${RABBITMQ_ENABLED:true}")
    private boolean mqEnabled;

    public void send(Long orderId, Long voucherId, Long userId) {
        if (!mqEnabled) {
            log.debug("RABBITMQ_ENABLED=false，跳过订单超时消息 orderId={}", orderId);
            return;
        }
        OrderTimeoutMessage message = new OrderTimeoutMessage(orderId, voucherId, userId, System.currentTimeMillis());
        rabbitTemplate.convertAndSend(RabbitMQConfig.DELAY_EXCHANGE, RabbitMQConfig.DELAY_ROUTING_KEY, message,
                msg -> {
                    // 消息级 TTL：该消息在延迟队列中存放 timeoutMs 后过期，被路由到取消队列
                    msg.getMessageProperties().setExpiration(String.valueOf(timeoutMs));
                    msg.getMessageProperties().setMessageId(String.valueOf(orderId));
                    return msg;
                });
        log.info("订单超时消息已投递 orderId={}, ttl={}ms", orderId, timeoutMs);
    }
}
