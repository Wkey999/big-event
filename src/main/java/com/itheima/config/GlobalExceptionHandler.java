package com.itheima.config;

import com.itheima.exception.BusinessException;
import com.itheima.pojo.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String INTERNAL_ERROR = "服务器内部错误";

    /**
     * 业务异常：service/controller 主动抛出，message 是面向用户的提示，原样回传给前端展示。
     */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        return Result.error(e.getMessage());
    }

    /**
     * 撞唯一索引（并发下可能绕过 service 层查重）。
     * 必须比 Exception 更具体的处理器先接住，否则会把带 SQL 语句的原始异常信息返回给客户端。
     */
    @ExceptionHandler(DuplicateKeyException.class)
    public Result<Void> handleDuplicateKeyException(DuplicateKeyException e) {
        log.warn("唯一索引冲突", e);
        return Result.error("数据已存在，请勿重复添加");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .findFirst()
                .orElse("参数校验失败");
        return Result.error(message);
    }

    /**
     * 请求体 JSON 解析失败（格式错误/类型不匹配），给前端可读的提示而不是笼统 500。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleNotReadable(HttpMessageNotReadableException e) {
        log.warn("请求体解析失败: {}", e.getMessage());
        return Result.error("请求体格式错误");
    }

    /**
     * 上传超过 multipart 限制（application.yml 配的单文件 10MB / 单请求 20MB）。
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Result<Void> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        log.warn("上传文件超限: {}", e.getMessage());
        return Result.error("文件大小超出限制（单文件最大 10MB）");
    }

    /**
     * 兜底：框架异常（DataAccessException 等）的 message 里带着 SQL 和表结构，
     * 一律按内部错误处理，只记日志不回传原文。
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("未处理异常", e);
        return Result.error(INTERNAL_ERROR);
    }
}
