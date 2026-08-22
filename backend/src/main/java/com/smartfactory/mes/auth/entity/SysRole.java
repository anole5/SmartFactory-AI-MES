package com.smartfactory.mes.auth.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.smartfactory.mes.auth.enums.UserStatus;
import com.smartfactory.mes.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 角色（RBAC）
 */
@Getter
@Setter
@TableName("sys_role")
public class SysRole extends BaseEntity {

    /** 角色编码（如 SUPER_ADMIN/OPERATOR/PLANNING，唯一性 Service 层校验） */
    private String roleCode;

    /** 角色名称 */
    private String roleName;

    /** 状态：ENABLED 启用 / DISABLED 停用 */
    private UserStatus status;

    /** 备注 */
    private String remark;
}
