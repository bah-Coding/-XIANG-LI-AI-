package com.hmdp.dto;

import com.hmdp.common.ErrorCode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ResultTest {

    @Test
    void okShouldCarrySuccessCode() {
        Result result = Result.ok("data");

        assertThat(result.getSuccess()).isTrue();
        assertThat(result.getCode()).isEqualTo(ErrorCode.SUCCESS.getCode());
        assertThat(result.getErrorMsg()).isNull();
        assertThat(result.getData()).isEqualTo("data");
    }

    @Test
    void okWithTotalShouldKeepTotal() {
        Result result = Result.ok(List.of("a", "b"), 2L);

        assertThat(result.getSuccess()).isTrue();
        assertThat(result.getTotal()).isEqualTo(2L);
    }

    @Test
    void failWithErrorCodeShouldMapCodeAndMessage() {
        Result result = Result.fail(ErrorCode.PARAM_ERROR);

        assertThat(result.getSuccess()).isFalse();
        assertThat(result.getCode()).isEqualTo(40000);
        assertThat(result.getErrorMsg()).isEqualTo("参数错误");
    }

    @Test
    void failWithCustomCodeShouldKeepBoth() {
        Result result = Result.fail(ErrorCode.CONFLICT, "优惠券已抢光");

        assertThat(result.getCode()).isEqualTo(40900);
        assertThat(result.getErrorMsg()).isEqualTo("优惠券已抢光");
    }

    @Test
    void legacyFailShouldFallBackToInternalErrorCode() {
        Result result = Result.fail("旧调用方式");

        assertThat(result.getSuccess()).isFalse();
        assertThat(result.getCode()).isEqualTo(ErrorCode.INTERNAL_ERROR.getCode());
        assertThat(result.getErrorMsg()).isEqualTo("旧调用方式");
    }
}
