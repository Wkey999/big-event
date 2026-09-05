package com.itheima.config;

import com.itheima.pojo.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String INTERNAL_ERROR = "服务器内部错误";

    /**
     * 撞唯一索引（并发下可能绕过 service 层查重）。
     * 必须比 RuntimeException 更具体的处理器先接住，否则会把带 SQL 语句的原始异常信息返回给客户端。
     */
    @ExceptionHandler(DuplicateKeyException.class)
    public Result<Void> handleDuplicateKeyException(DuplicateKeyException e) {
        log.warn("唯一索引冲突", e);
        return Result.error("数据已存在，请勿重复添加");
    }

    /**
     * 业务提示约定为 service/controller 抛出的「裸」RuntimeException，可以放心回传给前端；
     * 其子类（DataAccessException 等框架异常）的 message 里带着 SQL 和表结构，按内部错误处理。
     */
    @ExceptionHandler(RuntimeException.class)
    public Result<Void> handleRuntimeException(RuntimeException e) {
        if (RuntimeException.class.equals(e.getClass())) {
            return Result.error(e.getMessage());
        }
        return internalError(e);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .findFirst()
                .orElse("参数校验失败");
        return Result.error(message);
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        return internalError(e);
    }

    private Result<Void> internalError(Throwable e) {
        log.error("未处理异常", e);
        return Result.error(INTERNAL_ERROR);
    }
}
