package com.lijunjie.wms.common.context;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 当前请求的登录用户上下文。
 *
 * <p>由 {@code AuthInterceptor} 在校验令牌后写入，请求结束时清除。
 * 使用 {@link ThreadLocal} 保存，因此只在处理该请求的线程内可见；
 * 拦截器在 {@code afterCompletion} 中必须调用 {@link #clear()}，
 * 否则线程被回收复用时会残留上一个请求的身份，造成越权。</p>
 */
public final class UserContext {

    private static final ThreadLocal<CurrentUser> HOLDER = new ThreadLocal<CurrentUser>();

    private UserContext() {
    }

    /** 当前登录用户 */
    public static final class CurrentUser {
        private final Long userId;
        private final String username;
        private final List<String> roles;
        private final Set<String> permissions;

        public CurrentUser(Long userId, String username, List<String> roles, Set<String> permissions) {
            this.userId = userId;
            this.username = username;
            this.roles = roles == null ? Collections.<String>emptyList() : roles;
            this.permissions = permissions == null ? Collections.<String>emptySet() : permissions;
        }

        public Long getUserId() {
            return userId;
        }

        public String getUsername() {
            return username;
        }

        public List<String> getRoles() {
            return roles;
        }

        public Set<String> getPermissions() {
            return permissions;
        }

        /** 是否拥有指定权限 */
        public boolean hasPermission(String permission) {
            return permission != null && permissions.contains(permission);
        }
    }

    public static void set(CurrentUser user) {
        HOLDER.set(user);
    }

    public static CurrentUser get() {
        return HOLDER.get();
    }

    /** 清除，必须在请求结束时调用 */
    public static void clear() {
        HOLDER.remove();
    }
}
