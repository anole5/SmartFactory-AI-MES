package com.smartfactory.mes.auth.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.smartfactory.mes.auth.enums.UserStatus;
import com.smartfactory.mes.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 菜单（目录/菜单/按钮三级，RuoYi 风格 sys_menu）
 *
 * <p>perm 为权限标识（如 production:work-order:release）：
 * 第 2 周用于后端接口校验与前端按钮级权限；第 3/4 周接前端动态路由。</p>
 */
@Getter
@Setter
@TableName("sys_menu")
public class SysMenu extends BaseEntity {

    /** 父菜单 ID（0 = 根目录） */
    private Long parentId;

    /** 菜单名称 */
    private String menuName;

    /** 类型：M 目录 / C 菜单 / F 按钮 */
    private String menuType;

    /** 前端路由路径（C 级，如 /work-orders） */
    private String path;

    /** 权限标识（按钮级必有，如 master:product:create） */
    private String perm;

    /** 图标名（Element Plus 图标） */
    private String icon;

    /** 排序号 */
    private Integer orderNum;

    /** 状态：ENABLED 启用 / DISABLED 停用 */
    private UserStatus status;
}
