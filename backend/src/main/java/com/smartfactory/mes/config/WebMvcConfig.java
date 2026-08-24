package com.smartfactory.mes.config;

import com.smartfactory.mes.auth.AuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置：注册登录鉴权拦截器
 *
 * <p>注意：拦截路径是相对 context-path(/api) 的，"/**" 即 /api/**；
 * 登录接口与 error 转发除外（error 不放行会导致异常页面被 401 覆盖）。
 * 第 8 周追加 swagger/actuator 白名单：文档 UI 与健康检查匿名可访问
 * （actuator 暴露面由 application.yml 的 exposure.include 收窄为 health/info，
 * 白名单放行不等于暴露全量端点）。</p>
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
                .addPathPatterns("/**")
                .excludePathPatterns("/auth/login", "/error",
                        "/swagger-ui.html", "/swagger-ui/**",
                        "/v3/api-docs/**", "/actuator/**");
    }
}
