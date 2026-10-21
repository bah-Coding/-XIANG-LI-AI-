package com.hmdp.common;

/**
 * 统一错误码。
 * 约定：0 成功；4xxxx 客户端错误；5xxxx 服务端错误。
 * 前端只依赖 success/errorMsg，code 用于排查与监控，属于向后兼容的增量字段。
 */
public enum ErrorCode {

    SUCCESS(0, "ok"),
    PARAM_ERROR(40000, "参数错误"),
    UNAUTHORIZED(40100, "未登录或登录已过期"),
    FORBIDDEN(40300, "无权限"),
    NOT_FOUND(40400, "资源不存在"),
    CONFLICT(40900, "资源冲突"),
    TOO_MANY_REQUESTS(42900, "请求过于频繁"),
    INTERNAL_ERROR(50000, "服务器异常");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
