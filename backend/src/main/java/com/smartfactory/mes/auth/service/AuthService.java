package com.smartfactory.mes.auth.service;

import com.smartfactory.mes.auth.dto.LoginDTO;
import com.smartfactory.mes.auth.dto.LoginVO;
import com.smartfactory.mes.auth.entity.SysUser;

import java.util.List;

/**
 * 认证服务：登录签发 token + 查询启用用户（派工下拉用）
 */
public interface AuthService {

    /**
     * 登录：校验用户名密码（BCrypt）与账号状态，签发 JWT，
     * 返回 token + 用户信息 + 角色/权限标识集合（前端按钮级权限用）
     */
    LoginVO login(LoginDTO dto);

    /** 启用状态用户列表（派工选择操作员用；password 已 @JsonIgnore 不外泄） */
    List<SysUser> listEnabledUsers();
}
