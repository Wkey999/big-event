package com.itheima.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一响应结果封装
 * code: 0-成功, 1-失败
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {
    /** 响应码：0-成功 1-失败 */
    private Integer code;
    /** 提示信息 */
    private String message;
    /** 响应数据 */
    private T data;

    /** 成功（带数据） */
    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.setCode(0);
        result.setMessage("success");
        result.setData(data);
        return result;
    }

    /** 成功（无数据） */
    public static <T> Result<T> success() {
        return success(null);
    }

    /** 失败（带错误信息） */
    public static <T> Result<T> error(String message) {
        Result<T> result = new Result<>();
        result.setCode(1);
        result.setMessage(message);
        return result;
    }
}
