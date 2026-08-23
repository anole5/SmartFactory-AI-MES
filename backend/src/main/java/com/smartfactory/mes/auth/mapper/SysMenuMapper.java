package com.smartfactory.mes.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartfactory.mes.auth.entity.SysMenu;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 菜单 mapper
 */
public interface SysMenuMapper extends BaseMapper<SysMenu> {

    /**
     * 用户的权限标识集合：sys_user_role → sys_role_menu → sys_menu 三表 join
     *
     * <p>每次请求实时查库（不缓存）——权限变更即时生效；
     * 生产环境应加 Redis 缓存并随权限变更失效（面试点，见 AuthInterceptor 注释）。</p>
     */
    @Select("SELECT DISTINCT m.perm FROM sys_menu m "
            + "JOIN sys_role_menu rm ON rm.menu_id = m.id "
            + "JOIN sys_user_role ur ON ur.role_id = rm.role_id "
            + "WHERE ur.user_id = #{userId} "
            + "AND m.perm IS NOT NULL AND m.perm <> '' "
            + "AND m.deleted = 0 AND m.status = 'ENABLED'")
    List<String> listPermsByUserId(@Param("userId") Long userId);

    /**
     * 用户的菜单行集合（第 5 周前端动态路由数据源）：三表 join 同 listPermsByUserId，
     * 只取目录/菜单（M/C），按钮（F）是权限标识不进菜单树；按 order_num 排序。
     *
     * <p>DISTINCT 兜底：同一菜单经多个角色授权时去重。
     * 与 listPermsByUserId 同为实时查库不缓存（见其注释）。</p>
     */
    @Select("SELECT DISTINCT m.* FROM sys_menu m "
            + "JOIN sys_role_menu rm ON rm.menu_id = m.id "
            + "JOIN sys_user_role ur ON ur.role_id = rm.role_id "
            + "WHERE ur.user_id = #{userId} "
            + "AND m.menu_type IN ('M', 'C') "
            + "AND m.deleted = 0 AND m.status = 'ENABLED' "
            + "ORDER BY m.order_num ASC, m.id ASC")
    List<SysMenu> listMenusByUserId(@Param("userId") Long userId);
}
