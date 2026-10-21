package com.hmdp.mq;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.SeckillVoucherMapper;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.utils.RedisConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 订单超时取消的业务逻辑：
 * 1. 用「状态 CAS」保证只取消未支付订单（幂等，重复消息无副作用）；
 * 2. 取消成功后回滚数据库库存、Redis 库存，并移除 Redis 的「已下单」标记，允许用户重新下单。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderTimeoutService {

    public static final int STATUS_UNPAID = 1;
    public static final int STATUS_CANCELLED = 4;

    private final VoucherOrderMapper voucherOrderMapper;
    private final SeckillVoucherMapper seckillVoucherMapper;
    private final StringRedisTemplate stringRedisTemplate;

    @Transactional(rollbackFor = Exception.class)
    public boolean cancelIfUnpaid(Long orderId) {
        if (orderId == null) {
            return false;
        }
        VoucherOrder order = voucherOrderMapper.selectById(orderId);
        if (order == null) {
            log.warn("订单不存在，忽略超时取消 orderId={}", orderId);
            return false;
        }

        // CAS：只有仍处于「未支付」状态才取消，已支付/已取消的重复消息直接跳过
        int updated = voucherOrderMapper.update(null, new LambdaUpdateWrapper<VoucherOrder>()
                .eq(VoucherOrder::getId, orderId)
                .eq(VoucherOrder::getStatus, STATUS_UNPAID)
                .set(VoucherOrder::getStatus, STATUS_CANCELLED)
                .set(VoucherOrder::getUpdateTime, LocalDateTime.now()));
        if (updated == 0) {
            log.info("订单状态已变更，跳过超时取消 orderId={}", orderId);
            return false;
        }

        // 回滚数据库库存
        seckillVoucherMapper.update(null, new LambdaUpdateWrapper<SeckillVoucher>()
                .eq(SeckillVoucher::getVoucherId, order.getVoucherId())
                .setSql("stock = stock + 1"));
        // 回滚 Redis 库存
        stringRedisTemplate.opsForValue().increment(RedisConstants.SECKILL_STOCK_KEY + order.getVoucherId());
        // 移除「该用户已下单」标记，允许重新抢购
        if (order.getUserId() != null) {
            stringRedisTemplate.opsForSet().remove(
                    RedisConstants.SECKILL_ORDER_KEY + order.getVoucherId(), order.getUserId().toString());
        }

        log.info("订单超时未支付已取消并回滚库存 orderId={}, voucherId={}, userId={}",
                orderId, order.getVoucherId(), order.getUserId());
        return true;
    }
}
