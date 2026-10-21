package com.hmdp.mq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 订单超时消费者。
 * 失败重试由 spring.rabbitmq.listener.simple.retry 控制（默认 3 次），
 * 重试耗尽后消息被拒绝并进入死信队列 order.cancel.dlq。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutConsumer {

    private final OrderTimeoutService orderTimeoutService;

    // RABBITMQ_ENABLED=false 时可以不启动监听（本机没有 broker 时避免刷屏重连日志）
    @RabbitListener(queues = RabbitMQConfig.CANCEL_QUEUE, autoStartup = "${RABBITMQ_ENABLED:true}")
    public void onOrderTimeout(OrderTimeoutMessage message) {
        log.info("收到订单超时消息 orderId={}", message == null ? null : message.getOrderId());
        if (message == null || message.getOrderId() == null) {
            log.warn("订单超时消息体为空，丢弃");
            return;
        }
        orderTimeoutService.cancelIfUnpaid(message.getOrderId());
    }
}
