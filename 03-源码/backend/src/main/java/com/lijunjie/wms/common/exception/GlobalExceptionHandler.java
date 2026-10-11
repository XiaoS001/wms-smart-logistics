package com.lijunjie.wms.common.exception;

import com.lijunjie.wms.common.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * 全局异常处理器。
 *
 * <p>把各类异常统一转换为 {@link Result} 结构，避免异常堆栈直接暴露给前端，
 * 同时保证前端拿到的错误格式与正常响应一致。</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 业务异常：按异常自带的提示与状态码返回 */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusiness(BusinessException e) {
        log.warn("business exception: code={}, message={}", e.getCode(), e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    /** 参数校验失败（@Valid 校验 @RequestBody 时抛出） */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidation(MethodArgumentNotValidException e) {
        String message = firstErrorMessage(e.getBindingResult().getFieldErrors());
        log.warn("parameter validation failed: {}", message);
        return Result.error(400, message);
    }

    /** 参数绑定失败（表单参数校验时抛出） */
    @ExceptionHandler(BindException.class)
    public Result<Void> handleBind(BindException e) {
        String message = firstErrorMessage(e.getBindingResult().getFieldErrors());
        log.warn("parameter binding failed: {}", message);
        return Result.error(400, message);
    }

    /** 兜底：未预期的异常，记录完整堆栈但不向前端暴露细节 */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleOther(Exception e) {
        log.error("unexpected exception", e);
        return Result.error(500, "服务器内部错误，请联系管理员");
    }

    private String firstErrorMessage(List<FieldError> errors) {
        if (errors == null || errors.isEmpty()) {
            return "参数不合法";
        }
        FieldError first = errors.get(0);
        return first.getDefaultMessage() == null ? "参数不合法" : first.getDefaultMessage();
    }
}
