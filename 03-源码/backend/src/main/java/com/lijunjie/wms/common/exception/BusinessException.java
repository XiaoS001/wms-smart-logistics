package com.lijunjie.wms.common.exception;

/**
 * 业务异常。
 *
 * <p>用于表达"可以预期、需要向用户提示"的错误，例如登录名或密码错误、
 * 用户名已存在等。由全局异常处理器统一转换为标准响应结构。</p>
 */
public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 业务状态码，默认 500 */
    private final int code;

    public BusinessException(String message) {
        this(500, message);
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
