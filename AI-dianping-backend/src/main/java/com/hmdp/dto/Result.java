package com.hmdp.dto;

import com.hmdp.common.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result {
    private Boolean success;
    /** 统一错误码，0 表示成功；前端可继续只依赖 success */
    private Integer code;
    private String errorMsg;
    private Object data;
    private Long total;

    public Result(Boolean success, String errorMsg, Object data, Long total) {
        this.success = success;
        this.errorMsg = errorMsg;
        this.data = data;
        this.total = total;
        this.code = Boolean.TRUE.equals(success) ? ErrorCode.SUCCESS.getCode() : ErrorCode.INTERNAL_ERROR.getCode();
    }

    public static Result ok(){
        return new Result(true, ErrorCode.SUCCESS.getCode(), null, null, null);
    }
    public static Result ok(Object data){
        return new Result(true, ErrorCode.SUCCESS.getCode(), null, data, null);
    }
    public static Result ok(List<?> data, Long total){
        return new Result(true, ErrorCode.SUCCESS.getCode(), null, data, total);
    }
    public static Result fail(String errorMsg){
        return new Result(false, ErrorCode.INTERNAL_ERROR.getCode(), errorMsg, null, null);
    }
    public static Result fail(ErrorCode errorCode){
        return new Result(false, errorCode.getCode(), errorCode.getMessage(), null, null);
    }
    public static Result fail(ErrorCode errorCode, String errorMsg){
        return new Result(false, errorCode.getCode(), errorMsg, null, null);
    }
    public static Result fail(int code, String errorMsg){
        return new Result(false, code, errorMsg, null, null);
    }
}
