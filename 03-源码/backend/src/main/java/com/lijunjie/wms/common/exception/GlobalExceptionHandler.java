package com.lijunjie.wms.common.exception;

import com.lijunjie.wms.common.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * 全局异常处理器。
 *
 * <p>设计要点：<b>HTTP 状态码与响应体中的 code 保持一致</b>。这样前端既能通过
 * HTTP 状态码做统一拦截（例如 401 跳转登录页），也能读取响应体获得可展示的提示文案。</p>
 *
 * <p>日志级别按"责任方"区分：客户端问题（参数错误、请求体格式错误）记 WARN；
 * 服务端问题（未预期异常）记 ERROR 并保留完整堆栈。</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 业务异常：按异常自带的提示与状态码返回 */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusiness(BusinessException e) {
        log.warn("business exception: code={}, message={}", e.getCode(), e.getMessage());
        HttpStatus status = resolveStatus(e.getCode());
        Result<Void> body = Result.error(e.getCode(), e.getMessage());
        return ResponseEntity.status(status).body(body);
    }

    /** 参数校验失败（@Valid 校验 @RequestBody 时抛出） */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleValidation(MethodArgumentNotValidException e) {
        String message = firstErrorMessage(e.getBindingResult().getFieldErrors());
        log.warn("parameter validation failed: {}", message);
        return ResponseEntity.badRequest().body(Result.error(400, message));
    }

    /** 参数绑定失败（表单参数校验时抛出） */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<Result<Void>> handleBind(BindException e) {
        String message = firstErrorMessage(e.getBindingResult().getFieldErrors());
        log.warn("parameter binding failed: {}", message);
        return ResponseEntity.badRequest().body(Result.error(400, message));
    }

    /**
     * 请求体无法解析（JSON 格式错误、字段类型不匹配、缺少请求体）。
     *
     * <p>这属于客户端问题，必须返回 400 而不是 500，否则会被误判为服务端故障。</p>
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> handleUnreadable(HttpMessageNotReadableException e) {
        String detail = e.getMostSpecificCause() == null ? "" : e.getMostSpecificCause().getMessage();
        log.warn("request body not readable: {}", detail);
        return ResponseEntity.badRequest()
                .body(Result.error(400, "请求体格式错误，请检查 JSON 结构"));
    }

    /** 缺少必填的请求参数 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Result<Void>> handleMissingParam(MissingServletRequestParameterException e) {
        String message = "缺少必填参数：" + e.getParameterName();
        log.warn("missing request parameter: {}", e.getParameterName());
        return ResponseEntity.badRequest().body(Result.error(400, message));
    }

    /** 请求方法不支持 */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Result<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.warn("http method not supported: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(Result.error(405, "请求方法不支持：" + e.getMethod()));
    }

    /** 兜底：未预期的异常，记录完整堆栈但不向前端暴露细节 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleOther(Exception e) {
        log.error("unexpected exception", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.error(500, "服务器内部错误，请联系管理员"));
    }

    /**
     * 把业务状态码映射为合适的 HTTP 状态码。
     *
     * <p>只映射有明确语义的取值；其余一律按 500 处理，避免把任意数字当成 HTTP 状态码。</p>
     */
    private HttpStatus resolveStatus(int code) {
        switch (code) {
            case 400:
                return HttpStatus.BAD_REQUEST;
            case 401:
                return HttpStatus.UNAUTHORIZED;
            case 403:
                return HttpStatus.FORBIDDEN;
            case 404:
                return HttpStatus.NOT_FOUND;
            case 409:
                return HttpStatus.CONFLICT;
            case 429:
                return HttpStatus.TOO_MANY_REQUESTS;
            default:
                return HttpStatus.INTERNAL_SERVER_ERROR;
        }
    }

    private String firstErrorMessage(List<FieldError> errors) {
        if (errors == null || errors.isEmpty()) {
            return "参数不合法";
        }
        FieldError first = errors.get(0);
        return first.getDefaultMessage() == null ? "参数不合法" : first.getDefaultMessage();
    }
}
