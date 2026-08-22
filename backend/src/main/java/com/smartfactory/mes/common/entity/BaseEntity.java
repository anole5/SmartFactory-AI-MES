package com.smartfactory.mes.common.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 实体通用基类：主键 + 租户 + 审计字段 + 逻辑删除（全模块共享）
 *
 * <p>所有业务表统一使用这套通用字段（见 sql/01-schema.sql 的设计约定）：
 * id / tenant_id / created_by / created_at / updated_by / updated_at / deleted</p>
 */
@Getter
@Setter
public abstract class BaseEntity implements Serializable {

    /** 主键：数据库 AUTO_INCREMENT（IdType.AUTO） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 租户 ID：第一版固定默认租户 1，暂不做租户隔离拦截器（多租户是后续主题） */
    private Long tenantId = 1L;

    /** 创建人：自动填充（第 2 周起从 CurrentUserContext 取当前登录用户，未登录时 0） */
    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    /** 创建时间：自动填充 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新人：自动填充 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;

    /** 更新时间：自动填充 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 逻辑删除：0 否 / 1 是，查询和删除由 MyBatis-Plus 自动处理 */
    @TableLogic
    private Integer deleted;
}
