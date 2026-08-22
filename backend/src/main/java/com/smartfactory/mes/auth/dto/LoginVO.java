package com.smartfactory.mes.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 登录出参：token + 用户信息 + 角色/权限标识集合
 *
 * <p>roles/permissions 由 T4 RBAC 落库后填充；前端据此做按钮级权限（v-permission）。</p>
 */
@Getter
@Setter
@AllArgsConstructor
public class LoginVO {

    /** JWT（前端存 localStorage，请求头 Authorization: Bearer 携带） */
    private String token;

    private String username;

    private Long userId;

    /** 真实姓名/昵称（前端顶栏展示） */
    private String realName;

    /** 角色编码集合，如 ["SUPER_ADMIN"] */
    private List<String> roles;

    /** 权限标识集合，如 ["production:work-order:release"] */
    private List<String> permissions;
}
