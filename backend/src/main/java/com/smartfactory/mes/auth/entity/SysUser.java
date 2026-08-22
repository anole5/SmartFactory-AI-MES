package com.smartfactory.mes.auth.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.smartfactory.mes.auth.enums.UserStatus;
import com.smartfactory.mes.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 系统用户（RBAC，RuoYi 风格 sys_user）
 */
@Getter
@Setter
@TableName("sys_user")
public class SysUser extends BaseEntity {

    /** 登录账号（唯一性由 Service 层校验，见第 1 周逻辑删除与唯一索引决策） */
    private String username;

    /** 密码（BCrypt 哈希；@JsonIgnore：任何接口都不把密码序列化出去） */
    @JsonIgnore
    private String password;

    /** 真实姓名/昵称（前端顶栏展示） */
    private String realName;

    /** 状态：ENABLED 启用 / DISABLED 停用（停用后无法登录，已有 token 也被拦截器拒绝） */
    private UserStatus status;

    /** 备注 */
    private String remark;
}
