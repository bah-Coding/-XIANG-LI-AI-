package com.hmdp.config;

import com.hmdp.common.BizException;
import com.hmdp.common.ErrorCode;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import jakarta.validation.Valid;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 验证全局异常处理把各类异常映射成正确的业务错误码。
 */
class WebExceptionAdviceTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new FakeController())
                .setControllerAdvice(new WebExceptionAdvice())
                .build();
    }

    @Test
    void bizExceptionShouldUseItsOwnCode() throws Exception {
        mockMvc.perform(get("/fake/biz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.NOT_FOUND.getCode()))
                .andExpect(jsonPath("$.errorMsg").value("店铺不存在"));
    }

    @Test
    void unexpectedExceptionShouldBeInternalError() throws Exception {
        mockMvc.perform(get("/fake/boom"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.INTERNAL_ERROR.getCode()))
                .andExpect(jsonPath("$.errorMsg").value("服务器异常"));
    }

    @Test
    void pathVariableTypeMismatchShouldBeParamError() throws Exception {
        mockMvc.perform(get("/fake/id/undefined"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_ERROR.getCode()))
                .andExpect(jsonPath("$.errorMsg").value("参数 id 格式不正确"));
    }

    @Test
    void invalidBodyShouldReportFieldMessages() throws Exception {
        mockMvc.perform(post("/fake/valid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"\",\"code\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_ERROR.getCode()))
                .andExpect(jsonPath("$.errorMsg").value(org.hamcrest.Matchers.containsString("手机号不能为空")))
                .andExpect(jsonPath("$.errorMsg").value(org.hamcrest.Matchers.containsString("验证码不能为空")));
    }

    @RestController
    static class FakeController {

        @GetMapping("/fake/biz")
        public Result biz() {
            throw new BizException(ErrorCode.NOT_FOUND, "店铺不存在");
        }

        @GetMapping("/fake/boom")
        public Result boom() {
            throw new IllegalStateException("boom");
        }

        @GetMapping("/fake/id/{id}")
        public Result byId(@PathVariable("id") Long id) {
            return Result.ok(id);
        }

        @PostMapping("/fake/valid")
        public Result valid(@Valid @RequestBody LoginFormDTO form) {
            return Result.ok();
        }
    }
}
