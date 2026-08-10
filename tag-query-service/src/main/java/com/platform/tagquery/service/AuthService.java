package com.platform.tagquery.service;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.platform.tagquery.exception.BizException;
import com.platform.tagquery.exception.ErrorCode;
import com.platform.tagquery.middleware.RequestIdFilter;
import com.platform.tagquery.model.entity.AppKey;
import com.platform.tagquery.model.entity.AppKeyDataSource;
import com.platform.tagquery.repository.mysql.AppKeyDataSourceMapper;
import com.platform.tagquery.repository.mysql.AppKeyMapper;
import com.platform.tagquery.util.SignatureUtil;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

/**
 * 鉴权 & 授权服务 —— 查询链路的第 2、3 步（PDF 查询链路 7 步中的两步）。
 *
 * 📖 为什么鉴权写在 Service 而不是拦截器：
 *    蓝图画过 AuthInterceptor，但拦截器里读 JSON Body 需要 ContentCachingRequestWrapper
 *    包一层（请求体流只能读一次），对新手坑多。鉴权四步全部只依赖请求 DTO，
 *    放在 Service 入口按顺序调用，检查顺序与 PDF 完全一致，且单元测试直接可测。
 *
 * 🔐 安全总览（每个方法对应一道防线，顺序不能乱）：
 *    ① AppKey 存在且启用   → 防伪造身份
 *    ② 时间戳在 5 分钟内   → 防重放
 *    ③ 签名校验            → 防篡改
 *    ④ 数据源授权          → 防越权（多租户隔离的核心！）
 */


@Service
public class AuthService {
    private static final long TIMESTAMP_TTL_MS = 5 * 60 * 1000L;

    private final AppKeyMapper appKeyMapper;
    private final AppKeyDataSourceMapper authMapper;
    private final LogService logService;

    public AuthService(AppKeyMapper appKeyMapper, AppKeyDataSourceMapper authMapper,
                       LogService logService) {
        this.appKeyMapper = appKeyMapper;
        this.authMapper = authMapper;
        this.logService = logService;
    }


    /** ① 校验 AppKey，成功返回实体（后续要用 appSecret 和 qpsLimit） */
    public AppKey validateAppKey(String appKey){
        AppKey entity = appKeyMapper.selectOne(
                new LambdaQueryWrapper<AppKey>().eq(AppKey::getAppKey, appKey));
        if(entity == null || entity.getStatus() == null || entity.getStatus() != 1){
            throw new BizException(ErrorCode.APP_KEY_INVALID);
        }
        return entity;
    }

    /** ② 校验时间戳（防重放） */
    public void validateTimestamp(Long timestamp){
        long now = System.currentTimeMillis();
        if(Math.abs(now - timestamp) > TIMESTAMP_TTL_MS){
            throw new BizException(ErrorCode.TIMESTAMP_EXPIRED);
        }
    }

    /** ③ 校验签名（防篡改） */
    public void validateSignature(String appKey, long timestamp, List<String> ids,
                                  String dataSourceId, String signature , String appSecret){
        String expected = SignatureUtil.sign(appKey, timestamp, ids, dataSourceId, appSecret);
        if(!SignatureUtil.verify(expected , signature)){
            throw new BizException(ErrorCode.SIGNATURE_INVALID);
        }
    }

    /**
     * ④ 校验 AppKey 是否有权访问数据源（数据隔离的生命线）。
     *
     * 🔐 安全：PDF 要求"不同客户之间的数据需要物理隔离，调用方只能查询自身授权客户的数据源"。
     *    这道校验是多租户系统被攻击时最先被试的地方，任何绕过都是重大事故。
     *    授权关系只查 app_key_data_source 映射表，不存在 = 无权，宁可误拒不可误放。
     */

    public void checkDataSourceAuth(Long appKeyId,String dataSourceId){
        Long count = authMapper.selectCount(
                new LambdaQueryWrapper<AppKeyDataSource>()
                        .eq(AppKeyDataSource::getAppKeyId, appKeyId)
                        .eq(AppKeyDataSource::getDataSourceId, dataSourceId)
        );
        if(count == null || count == 0){
            throw new BizException(ErrorCode.DATA_SOURCE_UNAUTHORIZED,dataSourceId);
        }
    }

    /**
     * 一键执行四道防线（Controller/查询服务入口调用）。
     *
     * ⚡ 性能：①②④ 各一次主键/索引查询，毫秒级；③ 是纯 CPU 计算。
     *    后续 QPS 高了可以把 AppKey 信息缓存到 Redis（TTL 5 分钟），Day 9 优化项。
     *
     * 📖 每道防线失败时记录对应日志（PDF 3.5）：①②③ → auth_failure_log；
     *    ④ 数据源授权失败 → authz_failure_log（权限失败是独立日志类型）。
     */

    public AppKey authenticate(String appKey,long timestamp , List<String> ids,
                               String dataSourceId,String signature){
        String sourceIp = currentSourceIp();

        AppKey entity;
        try {
            entity = validateAppKey(appKey);              // ①
        } catch (BizException e) {
            logService.logAuthFailure(appKey, null, null, "AppKey 不存在或已停用", sourceIp);
            throw e;
        }
        try {
            validateTimestamp(timestamp);                 // ②
        } catch (BizException e) {
            logService.logAuthFailure(appKey, null, false, "时间戳过期", sourceIp);
            throw e;
        }
        try {
            validateSignature(appKey, timestamp, ids, dataSourceId, signature,entity.getAppSecret()); // ③
        } catch (BizException e) {
            logService.logAuthFailure(appKey, false, true, "签名校验失败", sourceIp);
            throw e;
        }
        try {
            checkDataSourceAuth(entity.getId(), dataSourceId);   // ④
        } catch (BizException e) {
            logService.logAuthzFailure(appKey, dataSourceId, false, "数据源未授权", sourceIp);
            throw e;
        }
        return entity;
    }

    /** 取当前请求的来源 IP（由 RequestIdFilter 塞入请求属性） */
    private String currentSourceIp() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                Object ip = attrs.getRequest().getAttribute(RequestIdFilter.ATTR_SOURCE_IP);
                if (ip != null) {
                    return ip.toString();
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
