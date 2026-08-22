package com.smartfactory.mes.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口权限注解：标注在 Controller 方法（或类）上，
 * AuthInterceptor 校验当前登录用户是否持有对应权限标识，无权限直写 403。
 *
 * <p>权限标识格式：模块:实体:动作，如 production:work-order:release。
 * 与 Spring Security 的 @PreAuthorize("hasAuthority('...')") 等价，
 * 自研注解链路透明、适合学习项目讲清原理。</p>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {

    /** 权限标识，如 "master:product:create" */
    String value();
}
