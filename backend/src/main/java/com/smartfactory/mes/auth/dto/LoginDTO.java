package com.smartfactory.mes.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * 登录入参（第 1 周假登录：任意非空账号密码都通过，返回固定 token）
 */
@Getter
@Setter
public class LoginDTO {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;
}
