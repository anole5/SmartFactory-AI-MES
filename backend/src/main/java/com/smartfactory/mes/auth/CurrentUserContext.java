package com.smartfactory.mes.auth;

/**
 * 当前登录用户上下文（ThreadLocal 传递）
 *
 * <p>AuthInterceptor 在 preHandle 解析 token 后 set，afterCompletion 必须 clear——
 * Tomcat 线程池复用线程，不清理会串号（把 A 用户的操作记到 B 用户头上），
 * 与 RequestIdFilter 的 MDC 清理是同一个思路。</p>
 */
public final class CurrentUserContext {

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    private CurrentUserContext() {
    }

    public static void set(LoginUser user) {
        HOLDER.set(user);
    }

    public static LoginUser get() {
        return HOLDER.get();
    }

    /** 当前用户 ID；未登录（系统任务等场景）返回 0，审计字段兜底 */
    public static Long getUserIdOrZero() {
        LoginUser user = HOLDER.get();
        return user == null ? 0L : user.getId();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
