package com.payhub.merchant.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.payhub.common.BizException;
import com.payhub.merchant.entity.MerchantApp;
import com.payhub.merchant.mapper.MerchantAppMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 商户应用查询。
 *
 * <p>W1 很简单，但它是「信任链的起点」：
 * 请求进来先确认 appId 存在且启用，后面的金额、费率、结算都挂在 appId 上。
 */
@Service
@RequiredArgsConstructor
public class MerchantAppService {

    private final MerchantAppMapper merchantAppMapper;

    public MerchantApp getByAppId(String appId) {
        return merchantAppMapper.selectOne(
                Wrappers.<MerchantApp>lambdaQuery().eq(MerchantApp::getAppId, appId));
    }

    /**
     * 校验「商户号 + appId」这一对是否合法。
     * 只校验 appId 是不够的：appId 可能被冒用，必须和 merchantNo 匹配上。
     */
    public MerchantApp mustGet(String merchantNo, String appId) {
        MerchantApp app = getByAppId(appId);
        if (app == null || !app.getMerchantNo().equals(merchantNo)) {
            throw new BizException("2000", "商户应用不存在或商户号不匹配");
        }
        if (app.getStatus() == null || app.getStatus() != 1) {
            throw new BizException("2000", "商户应用已停用");
        }
        return app;
    }
}
