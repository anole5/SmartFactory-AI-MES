package com.smartfactory.mes.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.smartfactory.mes.auth.CurrentUserContext;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 通用审计字段自动填充：createdAt / updatedAt / createdBy / updatedBy
 *
 * <p>第 2 周起操作人从 CurrentUserContext（ThreadLocal）取当前登录用户 ID；
 * 未登录场景（如系统任务）返回 0。MyBatis 同步执行，与拦截器同线程，ThreadLocal 可见。</p>
 */
@Component
public class AuditMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        Long userId = CurrentUserContext.getUserIdOrZero();
        this.strictInsertFill(metaObject, "createdAt", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "createdBy", Long.class, userId);
        this.strictInsertFill(metaObject, "updatedBy", Long.class, userId);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
        this.strictUpdateFill(metaObject, "updatedBy", Long.class, CurrentUserContext.getUserIdOrZero());
    }
}
