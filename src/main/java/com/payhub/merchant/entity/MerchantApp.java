package com.payhub.merchant.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import org.apache.ibatis.type.JdbcType;

import java.time.LocalDateTime;

/**
 * 商户应用（谁在调我们的开放接口）。
 *
 * <p>一个商户可以有多个应用，每个应用有独立的 appId 和密钥——
 * 这是支付平台的标准模型：appId 决定了「这笔交易属于哪个业务」，
 * 后续分账、结算、对账都按 appId 维度统计。
 *
 * <p><b>注解约定</b>（全项目统一，后面 W2~W5 新增实体照抄）：
 * <ul>
 *   <li>{@code @TableName}：显式声明表名，不吃「类名驼峰转下划线」的默认猜测</li>
 *   <li>{@code @TableId(value, type)}：主键列名 + 生成策略，AUTO 表示交给数据库自增</li>
 *   <li>{@code @TableField(value)}：每个字段都显式写列名，改字段名时能一眼看出要改哪</li>
 *   <li>{@code jdbcType}：可为空的列显式指定，避免部分驱动在 null 时报 JdbcType OTHER</li>
 *   <li>{@code fill}：创建/更新时间由 MetaObjectHandler 自动填充，业务代码不用再手写</li>
 * </ul>
 */
@Data
@TableName("merchant_app")
public class MerchantApp {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField(value = "merchant_no", jdbcType = JdbcType.VARCHAR)
    private String merchantNo;

    @TableField(value = "app_id", jdbcType = JdbcType.VARCHAR)
    private String appId;

    @TableField(value = "app_name", jdbcType = JdbcType.VARCHAR)
    private String appName;

    /** 商户公钥：W1 关闭验签时为空，打开验签后必须填 */
    @TableField(value = "merchant_public_key", jdbcType = JdbcType.VARCHAR)
    private String merchantPublicKey;

    @TableField(value = "notify_url", jdbcType = JdbcType.VARCHAR)
    private String notifyUrl;

    /** 1 启用 / 0 停用 */
    @TableField(value = "status", jdbcType = JdbcType.TINYINT)
    private Integer status;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
