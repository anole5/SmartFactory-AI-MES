package com.smartfactory.mes.auth.controller;

import com.smartfactory.mes.auth.dto.LoginDTO;
import com.smartfactory.mes.auth.dto.LoginVO;
import com.smartfactory.mes.common.api.ApiResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口（第 1 周简化登录：不校验用户名密码，直接发固定 token；
 * 第 2 周接真实用户表 + JWT + 权限拦截，见 README 进度说明）
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    /** 演示用固定 token，前端存 localStorage 并在请求头 Authorization 携带 */
    public static final String DEMO_TOKEN = "smartfactory-demo-token";

    @PostMapping("/login")
    public ApiResult<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        return ApiResult.success(new LoginVO(DEMO_TOKEN, dto.getUsername()));
    }
}
