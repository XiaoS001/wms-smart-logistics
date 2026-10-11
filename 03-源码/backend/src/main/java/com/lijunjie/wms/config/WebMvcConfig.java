package com.lijunjie.wms.config;

import com.lijunjie.wms.common.interceptor.AuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 层配置：注册认证与权限拦截器。
 *
 * <p>拦截器作用于 {@code /api/**} 的全部请求，白名单（登录、健康检查）
 * 由拦截器自身维护，避免路径规则分散在两处。</p>
 *
 * <p>不引入 Spring Security：本项目只需要「令牌认证 + 权限编码校验」两件事，
 * 用拦截器即可完成，依赖更少、故障面更小，也便于说明实现原理。</p>
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    public WebMvcConfig(AuthInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .order(1);
    }
}
