package com.payhub.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 自动填充 createTime / updateTime。
 *
 * <p>配合实体上的 {@code @TableField(fill = ...)} 使用。
 * 为什么值得单独抽一个类：
 * <ul>
 *   <li>时间字段不再散落在每个 service 里，避免「有的地方忘了 set」</li>
 *   <li>以后要统一换成数据库时间（比如多实例时钟不一致）只改这一处</li>
 * </ul>
 *
 * <p>注意 {@code strictInsertFill} 的语义：<b>字段为 null 时才填充</b>，
 * 业务代码显式 set 过的值不会被覆盖——这正好符合「允许覆盖默认值」的预期。
 */
@Component
public class PayHubMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }
}
