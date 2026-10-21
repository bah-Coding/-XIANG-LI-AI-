package com.hmdp.service.impl;

import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.utils.RedisConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UserServiceImpl 单元测试：不依赖真实 Redis / MySQL，全部 mock。
 */
@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    private UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        userService = new UserServiceImpl(userMapper);
        ReflectionTestUtils.setField(userService, "stringRedisTemplate", stringRedisTemplate);
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);
    }

    @Test
    void sendCodeShouldRejectInvalidPhone() {
        Result result = userService.sendCode("123", null);

        assertThat(result.getSuccess()).isFalse();
        assertThat(result.getErrorMsg()).isEqualTo("手机号格式错误");
        verify(valueOperations, never()).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));
    }

    @Test
    void sendCodeShouldStoreSixDigitCodeWithTtl() {
        Result result = userService.sendCode("13800000000", null);

        assertThat(result.getSuccess()).isTrue();

        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(
                eq(RedisConstants.LOGIN_CODE_KEY + "13800000000"),
                codeCaptor.capture(),
                eq(RedisConstants.LOGIN_CODE_TTL),
                eq(TimeUnit.MINUTES));
        assertThat(codeCaptor.getValue()).matches("\\d{6}");
    }

    @Test
    void loginShouldFailWhenCodeMismatch() {
        when(valueOperations.get(RedisConstants.LOGIN_CODE_KEY + "13800000000")).thenReturn("123456");

        LoginFormDTO form = new LoginFormDTO();
        form.setPhone("13800000000");
        form.setCode("000000");

        Result result = userService.login(form, null);

        assertThat(result.getSuccess()).isFalse();
        assertThat(result.getErrorMsg()).isEqualTo("验证码错误");
        verify(userMapper, never()).save(any(User.class));
    }

    @Test
    void loginShouldCreateUserAndStoreBackfilledIdInTokenHash() {
        String phone = "13800000000";
        when(valueOperations.get(RedisConstants.LOGIN_CODE_KEY + phone)).thenReturn("123456");
        when(userMapper.selectByPhone(phone)).thenReturn(null);
        // 模拟数据库自增主键回填（UserMapper.xml 里 useGeneratedKeys）
        doAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(1001L);
            return null;
        }).when(userMapper).save(any(User.class));

        LoginFormDTO form = new LoginFormDTO();
        form.setPhone(phone);
        form.setCode("123456");

        Result result = userService.login(form, null);

        assertThat(result.getSuccess()).isTrue();
        assertThat(result.getData()).isInstanceOf(String.class);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> mapCaptor = ArgumentCaptor.forClass(Map.class);
        verify(hashOperations).putAll(anyString(), mapCaptor.capture());
        Map<String, String> saved = mapCaptor.getValue();
        assertThat(saved.get("id")).isEqualTo("1001");
        assertThat(saved.get("nickName")).startsWith("user_");

        verify(stringRedisTemplate).expire(anyString(), anyLong(), eq(TimeUnit.MINUTES));
    }

    @Test
    void sendCodeShouldRejectWhenPhoneRateLimitExceeded() {
        when(valueOperations.increment(RedisConstants.LOGIN_CODE_LIMIT_KEY + "13800000001")).thenReturn(2L);

        Result result = userService.sendCode("13800000001", null);

        assertThat(result.getSuccess()).isFalse();
        assertThat(result.getErrorMsg()).contains("过于频繁");
        verify(valueOperations, never()).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));
    }
}
