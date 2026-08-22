package com.smartfactory.mes.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartfactory.mes.auth.entity.SysRole;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 角色 mapper
 */
public interface SysRoleMapper extends BaseMapper<SysRole> {

    /** 用户的角色编码集合（登录返回给前端，deleted 由逻辑删除过滤） */
    @Select("SELECT DISTINCT r.role_code FROM sys_role r "
            + "JOIN sys_user_role ur ON ur.role_id = r.id "
            + "WHERE ur.user_id = #{userId} AND r.deleted = 0")
    List<String> listRoleCodesByUserId(@Param("userId") Long userId);
}
