package com.smartfactory.mes.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/**
 * 角色-菜单关系（纯关系表：无逻辑删除，唯一键 uk_role_menu 防重）
 */
@Getter
@Setter
@TableName("sys_role_menu")
public class SysRoleMenu {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long roleId;

    private Long menuId;

    private Long tenantId = 1L;
}
