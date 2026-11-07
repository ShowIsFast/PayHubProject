package com.payhub.merchant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.payhub.merchant.entity.MerchantApp;

/**
 * MyBatis-Plus 的 BaseMapper 已经提供 insert/selectById/selectOne 等常用方法，
 * W1 不需要写一行 XML。复杂 SQL（对账的批量比对）再写 XML 或 @Select。
 */
public interface MerchantAppMapper extends BaseMapper<MerchantApp> {
}
