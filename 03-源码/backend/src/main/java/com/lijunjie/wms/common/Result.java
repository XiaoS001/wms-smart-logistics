package com.lijunjie.wms.common;

import java.io.Serializable;

/**
 * 统一响应结果包装类。
 *
 * <p>所有 REST 接口统一返回该结构，便于前端处理与接口文档生成：</p>
 * <pre>
 * {
 *   "code": 200,
 *   "message": "操作成功",
 *   "data": { ... },
 *   "timestamp": 1791697000000
 * }
 * </pre>
 *
 * @param <T> 业务数据类型
 */
public class Result<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 业务状态码：200 成功，其它为失败 */
    private Integer code;

    /** 提示信息 */
    private String message;

    /** 业务数据 */
    private T data;

    /** 服务端时间戳（毫秒） */
    private Long timestamp;

    public Result() {
        this.timestamp = System.currentTimeMillis();
    }

    public Result(Integer code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.timestamp = System.currentTimeMillis();
    }

    /** 成功（无数据） */
    public static <T> Result<T> success() {
        return new Result<T>(200, "操作成功", null);
    }

    /** 成功（带数据） */
    public static <T> Result<T> success(T data) {
        return new Result<T>(200, "操作成功", data);
    }

    /** 成功（自定义提示与数据） */
    public static <T> Result<T> success(String message, T data) {
        return new Result<T>(200, message, data);
    }

    /** 失败（默认 500） */
    public static <T> Result<T> error(String message) {
        return new Result<T>(500, message, null);
    }

    /** 失败（自定义状态码） */
    public static <T> Result<T> error(Integer code, String message) {
        return new Result<T>(code, message, null);
    }

    /** 是否为成功响应，供内部判断使用 */
    public boolean isSuccess() {
        return code != null && code == 200;
    }

    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }
}
