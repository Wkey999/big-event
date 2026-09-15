package com.itheima.exception;

/**
 * 业务异常：message 是面向用户的提示，全局异常处理器会原样回传给前端展示。
 *
 * 引入本类型前，业务提示与基础设施异常都混在 RuntimeException 里，
 * 只能靠「异常类恰好是 RuntimeException」这种脆弱约定区分；
 * 现在业务代码一律抛 BusinessException，其余 RuntimeException 子类
 * （DataAccessException 等框架异常）一律按内部错误处理，不再回传原文。
 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}
