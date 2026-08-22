package com.smartfactory.mes.auth.service;

import java.util.List;

/**
 * 权限查询服务：按用户查角色编码与权限标识集合
 */
public interface PermissionService {

    /** 用户的角色编码集合，如 ["SUPER_ADMIN"] */
    List<String> listRoleCodes(Long userId);

    /** 用户的权限标识集合，如 ["production:work-order:release", ...] */
    List<String> listPerms(Long userId);

    /** 用户是否持有某权限标识 */
    boolean hasPerm(Long userId, String perm);
}
