package com.smartfactory.mes.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartfactory.mes.auth.entity.SysUser;
import com.smartfactory.mes.auth.enums.UserStatus;
import com.smartfactory.mes.auth.mapper.SysUserMapper;
import com.smartfactory.mes.common.api.ApiResult;
import com.smartfactory.mes.common.api.ResultCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/**
 * 登录鉴权拦截器：解析 Bearer token → 校验用户 → 放入 CurrentUserContext
 *
 * <p>与 Spring Security 方案的对比（面试可讲）：自研拦截器链路透明可控——
 * 白名单、401/403 响应、ThreadLocal 传递都显式可见，学习项目首选；
 * 生产级系统一般用 Spring Security（过滤器链 + AuthenticationManager 体系）。</p>
 *
 * <p>性能说明：每次请求查一次 sys_user（T4 起还有权限 join 查询），
 * 演示规模无压力；生产环境应用 Redis 缓存「用户 + 权限」，权限变更时删缓存。</p>
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;
    private final SysUserMapper sysUserMapper;
    private final ObjectMapper objectMapper;

    public AuthInterceptor(JwtUtil jwtUtil, SysUserMapper sysUserMapper, ObjectMapper objectMapper) {
        this.jwtUtil = jwtUtil;
        this.sysUserMapper = sysUserMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        // OPTIONS 预检直接放行（dev 走 Vite proxy 同源无跨域，此为防御性写法）
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            writeUnauthorized(response, "未登录或登录已过期");
            return false;
        }
        try {
            Claims claims = jwtUtil.parse(authHeader.substring(BEARER_PREFIX.length()));
            Long userId = Long.valueOf(claims.getSubject());
            // 校验用户仍存在且启用：停用/删除后即使 token 未过期也拒绝
            SysUser user = sysUserMapper.selectById(userId);
            if (user == null || user.getStatus() != UserStatus.ENABLED) {
                writeUnauthorized(response, "账号不存在或已停用");
                return false;
            }
            CurrentUserContext.set(new LoginUser(user.getId(), user.getUsername(), user.getRealName()));
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            writeUnauthorized(response, "未登录或登录已过期");
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        // 线程复用场景必须清理，否则下一个请求会串当前用户
        CurrentUserContext.clear();
    }

    /** 401 直写 JSON：复用 ApiResult 结构；显式 charset=UTF-8 否则中文乱码 */
    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(
                ApiResult.error(ResultCode.UNAUTHORIZED, message)));
    }
}
