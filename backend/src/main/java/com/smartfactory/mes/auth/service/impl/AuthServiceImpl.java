package com.smartfactory.mes.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartfactory.mes.auth.JwtUtil;
import com.smartfactory.mes.auth.dto.LoginDTO;
import com.smartfactory.mes.auth.dto.LoginVO;
import com.smartfactory.mes.auth.entity.SysUser;
import com.smartfactory.mes.auth.enums.UserStatus;
import com.smartfactory.mes.auth.mapper.SysUserMapper;
import com.smartfactory.mes.auth.service.AuthService;
import com.smartfactory.mes.auth.service.PermissionService;
import com.smartfactory.mes.common.api.ResultCode;
import com.smartfactory.mes.common.exception.BusinessException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 认证服务实现：真实登录（替换第 1 周固定 token）
 */
@Service
public class AuthServiceImpl implements AuthService {

    /** BCrypt 编码器：每次 matches 自带盐值校验，无状态可共享单例 */
    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    private final SysUserMapper sysUserMapper;
    private final JwtUtil jwtUtil;
    private final PermissionService permissionService;

    public AuthServiceImpl(SysUserMapper sysUserMapper, JwtUtil jwtUtil,
                           PermissionService permissionService) {
        this.sysUserMapper = sysUserMapper;
        this.jwtUtil = jwtUtil;
        this.permissionService = permissionService;
    }

    @Override
    public LoginVO login(LoginDTO dto) {
        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, dto.getUsername()));
        // 用户不存在与密码错误返回同一提示，避免账号枚举攻击（面试小细节）
        if (user == null || !PASSWORD_ENCODER.matches(dto.getPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "用户名或密码错误");
        }
        if (user.getStatus() != UserStatus.ENABLED) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "账号已停用");
        }
        String token = jwtUtil.generate(user.getId(), user.getUsername());
        // 角色/权限集合随登录下发：前端据此做按钮级显示控制（v-permission）
        List<String> roles = permissionService.listRoleCodes(user.getId());
        List<String> permissions = permissionService.listPerms(user.getId());
        return new LoginVO(token, user.getUsername(), user.getId(), user.getRealName(),
                roles, permissions);
    }

    @Override
    public List<SysUser> listEnabledUsers() {
        return sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getStatus, UserStatus.ENABLED)
                .orderByAsc(SysUser::getId));
    }
}
