package com.lijunjie.wms.dto;

import java.io.Serializable;
import java.util.List;

/**
 * 登录响应结果。
 *
 * <p>前端拿到后通常这样使用：令牌存入本地存储，用于后续请求的
 * {@code Authorization: Bearer <token>} 头；{@code permissions} 用于按钮级权限控制；
 * {@code roles} 用于路由与菜单过滤。</p>
 */
public class LoginResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /** JWT 令牌 */
    private String token;

    /** 令牌有效期（小时） */
    private Long expiresInHours;

    /** 用户 ID */
    private Long userId;

    /** 登录名 */
    private String username;

    /** 真实姓名 */
    private String realName;

    /** 角色编码列表，如 [ADMIN] */
    private List<String> roles;

    /** 权限编码列表，如 [product:view, product:add] */
    private List<String> permissions;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public Long getExpiresInHours() {
        return expiresInHours;
    }

    public void setExpiresInHours(Long expiresInHours) {
        this.expiresInHours = expiresInHours;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getRealName() {
        return realName;
    }

    public void setRealName(String realName) {
        this.realName = realName;
    }

    public List<String> getRoles() {
        return roles;
    }

    public void setRoles(List<String> roles) {
        this.roles = roles;
    }

    public List<String> getPermissions() {
        return permissions;
    }

    public void setPermissions(List<String> permissions) {
        this.permissions = permissions;
    }
}
