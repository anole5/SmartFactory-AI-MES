package com.smartfactory.mes.auth;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 当前登录用户（拦截器解析 token 后放入 CurrentUserContext，Service 层直接取）
 */
@Getter
@AllArgsConstructor
public class LoginUser {

    /** 用户 ID（sys_user.id） */
    private final Long id;

    /** 登录名 */
    private final String username;

    /** 真实姓名/昵称 */
    private final String realName;
}
