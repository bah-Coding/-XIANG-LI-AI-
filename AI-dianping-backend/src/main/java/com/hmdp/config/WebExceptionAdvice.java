package com.hmdp.config;

import com.hmdp.common.BizException;
import com.hmdp.common.ErrorCode;
import com.hmdp.dto.Result;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 全局异常处理：把不同类型的异常映射成带错误码的 Result。
 * 注意：统一返回 HTTP 200 + success=false，与现有前端（只看 success/errorMsg）保持兼容。
 */
@Slf4j
@RestControllerAdvice
public class WebExceptionAdvice {

    /** 业务异常：可预期的失败，用 warn 级别，不打堆栈 */
    @ExceptionHandler(BizException.class)
    public Result handleBizException(BizException e) {
        log.warn("business exception: code={}, msg={}", e.getCode(), e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    /** @RequestBody + @Valid 校验失败 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        return Result.fail(ErrorCode.PARAM_ERROR, joinFieldErrors(e.getBindingResult().getFieldErrors()));
    }

    /** 表单/查询参数绑定校验失败 */
    @ExceptionHandler(BindException.class)
    public Result handleBindException(BindException e) {
        return Result.fail(ErrorCode.PARAM_ERROR, joinFieldErrors(e.getBindingResult().getFieldErrors()));
    }

    /** @RequestParam / @PathVariable 上的约束校验失败 */
    @ExceptionHandler(ConstraintViolationException.class)
    public Result handleConstraintViolation(ConstraintViolationException e) {
        String msg = e.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + " " + v.getMessage())
                .collect(Collectors.joining("; "));
        return Result.fail(ErrorCode.PARAM_ERROR, msg);
    }

    /** 缺少参数、参数类型不匹配（如 /user/info/undefined）、请求体格式错误 */
    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class
    })
    public Result handleBadRequest(Exception e) {
        log.warn("bad request: {}", e.getMessage());
        String detail = e instanceof MethodArgumentTypeMismatchException mismatch
                ? "参数 " + mismatch.getName() + " 格式不正确"
                : "请求参数格式不正确";
        return Result.fail(ErrorCode.PARAM_ERROR, detail);
    }

    /** 上传文件超限 */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Result handleMaxUploadSize(MaxUploadSizeExceededException e) {
        log.warn("upload too large: {}", e.getMessage());
        return Result.fail(ErrorCode.PARAM_ERROR, "文件大小超出限制");
    }

    /** 兜底：未预期异常，记录堆栈并按 500 返回 */
    @ExceptionHandler(Exception.class)
    public Result handleException(Exception e) {
        log.error("unexpected exception", e);
        return Result.fail(ErrorCode.INTERNAL_ERROR);
    }

    private String joinFieldErrors(List<FieldError> errors) {
        String msg = errors.stream()
                .map(f -> f.getField() + " " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return msg.isEmpty() ? "参数校验失败" : msg;
    }
}
