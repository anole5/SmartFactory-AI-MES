package com.smartfactory.mes.auth.service.impl;

import com.smartfactory.mes.auth.mapper.SysMenuMapper;
import com.smartfactory.mes.auth.mapper.SysRoleMapper;
import com.smartfactory.mes.auth.service.PermissionService;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 权限查询服务实现：三表 join 实时查库
 */
@Service
public class PermissionServiceImpl implements PermissionService {

    private final SysRoleMapper sysRoleMapper;
    private final SysMenuMapper sysMenuMapper;

    public PermissionServiceImpl(SysRoleMapper sysRoleMapper, SysMenuMapper sysMenuMapper) {
        this.sysRoleMapper = sysRoleMapper;
        this.sysMenuMapper = sysMenuMapper;
    }

    @Override
    public List<String> listRoleCodes(Long userId) {
        return sysRoleMapper.listRoleCodesByUserId(userId);
    }

    @Override
    public List<String> listPerms(Long userId) {
        return sysMenuMapper.listPermsByUserId(userId);
    }

    @Override
    public boolean hasPerm(Long userId, String perm) {
        // 单次查库后 Set 判断；请求内多次校验不重复查询
        Set<String> perms = new HashSet<>(listPerms(userId));
        return perms.contains(perm);
    }
}
