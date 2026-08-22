package com.smartfactory.mes.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/**
 * 登录出参（token 第 1 周为固定值，第 2 周接真实用户/权限体系）
 */
@Getter
@Setter
@AllArgsConstructor
public class LoginVO {

    private String token;

    private String username;
}
