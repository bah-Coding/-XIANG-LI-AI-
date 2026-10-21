package com.hmdp.mq;

import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.SeckillVoucherMapper;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.utils.RedisConstants;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 订单超时取消逻辑的单元测试：重点验证「状态 CAS 幂等」与「库存回滚」。
 */
@ExtendWith(MockitoExtension.class)
class OrderTimeoutServiceTest {

    @Mock
    private VoucherOrderMapper voucherOrderMapper;
    @Mock
    private SeckillVoucherMapper seckillVoucherMapper;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private SetOperations<String, String> setOperations;

    private OrderTimeoutService service;

    @BeforeAll
    static void initTableInfo() {
        // LambdaUpdateWrapper 需要 MyBatis-Plus 的 TableInfo 缓存；纯单元测试里手动初始化一次
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, VoucherOrder.class);
        TableInfoHelper.initTableInfo(assistant, com.hmdp.entity.SeckillVoucher.class);
    }

    @BeforeEach
    void setUp() {
        service = new OrderTimeoutService(voucherOrderMapper, seckillVoucherMapper, stringRedisTemplate);
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
    }

    @Test
    void shouldIgnoreMissingOrder() {
        when(voucherOrderMapper.selectById(99L)).thenReturn(null);

        assertThat(service.cancelIfUnpaid(99L)).isFalse();

        verify(voucherOrderMapper, never()).update(any(), any());
        verify(seckillVoucherMapper, never()).update(any(), any());
    }

    @Test
    void shouldSkipWhenStatusAlreadyChanged() {
        VoucherOrder order = new VoucherOrder().setId(1L).setVoucherId(10L).setUserId(100L).setStatus(2);
        when(voucherOrderMapper.selectById(1L)).thenReturn(order);
        // CAS 更新影响 0 行：说明订单已被支付/取消
        when(voucherOrderMapper.update(isNull(), any())).thenReturn(0);

        assertThat(service.cancelIfUnpaid(1L)).isFalse();

        verify(seckillVoucherMapper, never()).update(any(), any());
        verify(stringRedisTemplate, never()).opsForValue();
    }

    @Test
    void shouldCancelAndRollbackStockForUnpaidOrder() {
        VoucherOrder order = new VoucherOrder().setId(1L).setVoucherId(10L).setUserId(100L).setStatus(1);
        when(voucherOrderMapper.selectById(1L)).thenReturn(order);
        when(voucherOrderMapper.update(isNull(), any())).thenReturn(1);

        assertThat(service.cancelIfUnpaid(1L)).isTrue();

        // 数据库库存 +1
        verify(seckillVoucherMapper).update(isNull(), any());
        // Redis 库存 +1
        verify(valueOperations).increment(RedisConstants.SECKILL_STOCK_KEY + 10L);
        // 移除「已下单」标记，允许重新抢购
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(setOperations).remove(keyCaptor.capture(), eq("100"));
        assertThat(keyCaptor.getValue()).isEqualTo(RedisConstants.SECKILL_ORDER_KEY + 10L);
    }

    @Test
    void shouldIgnoreNullOrderId() {
        assertThat(service.cancelIfUnpaid(null)).isFalse();
        verify(voucherOrderMapper, never()).selectById(any());
    }
}
