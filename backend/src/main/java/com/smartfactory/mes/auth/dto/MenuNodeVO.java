package com.smartfactory.mes.auth.dto;

import com.smartfactory.mes.auth.entity.SysMenu;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 菜单树节点出参（第 5 周：前端动态路由数据源）
 *
 * <p>服务端内存组树后直接下发完整树：目录（M）为分组节点，菜单（C）带前端路由 path；
 * 按钮（F）不进树——按钮级控制仍走登录时下发的 permissions + v-permission。</p>
 */
@Getter
@Setter
public class MenuNodeVO {

    private Long id;

    private Long parentId;

    /** 菜单名称（前端取作路由 meta.title） */
    private String menuName;

    /** 类型：M 目录 / C 菜单 */
    private String menuType;

    /** 前端路由路径（仅 C 级非空，如 /erp-orders） */
    private String path;

    /** 权限标识（前端 hasPerm 按钮级控制用，可为空） */
    private String perm;

    /** 图标名（Element Plus 图标，前端 &lt;component :is&gt; 解析） */
    private String icon;

    private Integer orderNum;

    /** 子节点（目录下挂菜单；C 级恒为空列表） */
    private List<MenuNodeVO> children = new ArrayList<>();

    public static MenuNodeVO of(SysMenu menu) {
        MenuNodeVO vo = new MenuNodeVO();
        vo.setId(menu.getId());
        vo.setParentId(menu.getParentId());
        vo.setMenuName(menu.getMenuName());
        vo.setMenuType(menu.getMenuType());
        vo.setPath(menu.getPath());
        vo.setPerm(menu.getPerm());
        vo.setIcon(menu.getIcon());
        vo.setOrderNum(menu.getOrderNum());
        return vo;
    }
}
