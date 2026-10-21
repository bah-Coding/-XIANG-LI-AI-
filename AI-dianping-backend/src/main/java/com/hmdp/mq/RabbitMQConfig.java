package com.hmdp.mq;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 订单超时取消的 RabbitMQ 拓扑。
 *
 * <pre>
 * 下单成功 --(TTL 15min, 无消费者)--> order.delay.queue
 *                                        | 过期后由死信交换机转发
 *                                        v
 *                               order.cancel.queue  --消费者--> 取消订单 + 回滚库存
 *                                        | 消费异常且重试耗尽
 *                                        v
 *                                  order.dlx.exchange --> order.cancel.dlq（人工排查）
 * </pre>
 *
 * 说明：延迟用「消息级 TTL + 死信交换机（DLX）」实现，不依赖 delayed-message 插件；
 * 由于所有消息 TTL 相同，不存在队头阻塞问题。
 */
@Configuration
public class RabbitMQConfig {

    public static final String DELAY_EXCHANGE = "order.delay.exchange";
    public static final String DELAY_QUEUE = "order.delay.queue";
    public static final String DELAY_ROUTING_KEY = "order.delay";

    public static final String CANCEL_EXCHANGE = "order.cancel.exchange";
    public static final String CANCEL_QUEUE = "order.cancel.queue";
    public static final String CANCEL_ROUTING_KEY = "order.cancel";

    public static final String DLX_EXCHANGE = "order.dlx.exchange";
    public static final String DLQ = "order.cancel.dlq";
    public static final String DLQ_ROUTING_KEY = "order.dlq";

    @Bean
    public DirectExchange orderDelayExchange() {
        return new DirectExchange(DELAY_EXCHANGE, true, false);
    }

    @Bean
    public Queue orderDelayQueue() {
        return QueueBuilder.durable(DELAY_QUEUE)
                .deadLetterExchange(CANCEL_EXCHANGE)
                .deadLetterRoutingKey(CANCEL_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding orderDelayBinding() {
        return BindingBuilder.bind(orderDelayQueue()).to(orderDelayExchange()).with(DELAY_ROUTING_KEY);
    }

    @Bean
    public DirectExchange orderCancelExchange() {
        return new DirectExchange(CANCEL_EXCHANGE, true, false);
    }

    @Bean
    public Queue orderCancelQueue() {
        return QueueBuilder.durable(CANCEL_QUEUE)
                .deadLetterExchange(DLX_EXCHANGE)
                .deadLetterRoutingKey(DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding orderCancelBinding() {
        return BindingBuilder.bind(orderCancelQueue()).to(orderCancelExchange()).with(CANCEL_ROUTING_KEY);
    }

    @Bean
    public DirectExchange orderDlxExchange() {
        return new DirectExchange(DLX_EXCHANGE, true, false);
    }

    @Bean
    public Queue orderCancelDlq() {
        return QueueBuilder.durable(DLQ).build();
    }

    @Bean
    public Binding orderDlqBinding() {
        return BindingBuilder.bind(orderCancelDlq()).to(orderDlxExchange()).with(DLQ_ROUTING_KEY);
    }

    /** 消息体用 JSON 序列化，便于在管理台直接观察内容 */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
