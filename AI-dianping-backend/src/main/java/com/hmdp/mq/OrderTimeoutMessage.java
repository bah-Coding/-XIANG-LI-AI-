package com.hmdp.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 订单超时消息体。
 * 用普通类而不是 record，避免依赖 Jackson 的 record 反序列化配置。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderTimeoutMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long orderId;
    private Long voucherId;
    private Long userId;
    private Long createTime;
}
