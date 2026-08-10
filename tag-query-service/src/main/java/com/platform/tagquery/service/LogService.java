package com.platform.tagquery.service;

import com.platform.tagquery.model.entity.AuthFailureLog;
import com.platform.tagquery.model.entity.AuthzFailureLog;
import com.platform.tagquery.model.entity.QueryLog;
import com.platform.tagquery.model.entity.RateLimitLog;
import com.platform.tagquery.repository.mysql.AuthFailureLogMapper;
import com.platform.tagquery.repository.mysql.AuthzFailureLogMapper;
import com.platform.tagquery.repository.mysql.QueryLogMapper;
import com.platform.tagquery.repository.mysql.RateLimitLogMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 日志服务：查询/鉴权失败/限流日志写 MySQL（Day 4）。
 *
 * ⚡ 性能核心：@Async 异步写库。
 *    写日志是"重要但不紧急"的事，绝不能拖慢查询主链路（QPS 1000 时，
 *    每次查询同步写一次 MySQL 会让 RT 翻倍，MySQL 也会先被打死）。
 *    代价：极端情况下（服务崩溃）可能丢几条日志 —— 日志允许丢，响应不允许慢。
 *
 * 📖 配套：启动类 @EnableAsync + config/AsyncConfig 里的 logExecutor 线程池。
 */
@Slf4j
@Service
public class LogService {

    private final QueryLogMapper queryLogMapper;
    private final AuthFailureLogMapper authFailureLogMapper;
    private final RateLimitLogMapper rateLimitLogMapper;
    private final AuthzFailureLogMapper authzFailureLogMapper;

    public LogService(QueryLogMapper queryLogMapper,
                      AuthFailureLogMapper authFailureLogMapper,
                      RateLimitLogMapper rateLimitLogMapper,
                      AuthzFailureLogMapper authzFailureLogMapper) {
        this.queryLogMapper = queryLogMapper;
        this.authFailureLogMapper = authFailureLogMapper;
        this.rateLimitLogMapper = rateLimitLogMapper;
        this.authzFailureLogMapper = authzFailureLogMapper;
    }

    /** 查询成功/失败的调用日志 */
    @Async("logExecutor")
    public void logQuery(String requestId, String appKey, String dataSourceId,
                         int idCount, int hitCount, long costMs,
                         String status, String versionKey, String sourceIp) {
        try {
            QueryLog ql = new QueryLog();
            ql.setRequestId(requestId);
            ql.setAppKey(appKey);
            ql.setDataSourceId(dataSourceId);
            ql.setIdCount(idCount);
            ql.setHitCount(hitCount);
            ql.setCostMs(costMs);
            ql.setStatus(status);
            ql.setVersionKey(versionKey);
            ql.setSourceIp(sourceIp);
            queryLogMapper.insert(ql);
        } catch (Exception e) {
            // 📖 日志写失败绝不能影响业务，吞掉但留痕
            log.error("查询日志写库失败 requestId={}", requestId, e);
        }
    }

    /** 鉴权失败日志（🔐 安全审计：谁在尝试非法调用） */
    @Async("logExecutor")
    public void logAuthFailure(String appKey, Boolean signaturePassed, Boolean timestampPassed,
                               String failureReason, String sourceIp) {
        try {
            AuthFailureLog afl = new AuthFailureLog();
            afl.setAppKey(appKey);
            afl.setSignaturePassed(signaturePassed);
            afl.setTimestampPassed(timestampPassed);
            afl.setFailureReason(failureReason);
            afl.setSourceIp(sourceIp);
            authFailureLogMapper.insert(afl);
        } catch (Exception e) {
            log.error("鉴权失败日志写库失败 appKey={}", appKey, e);
        }
    }

    /** 限流日志 */
    @Async("logExecutor")
    public void logRateLimit(String appKey, String dimension, int thresholdVal, int currentValue) {
        try {
            RateLimitLog rll = new RateLimitLog();
            rll.setAppKey(appKey);
            rll.setDimension(dimension);
            rll.setThresholdVal(thresholdVal);
            rll.setCurrentValue(currentValue);
            rateLimitLogMapper.insert(rll);
        } catch (Exception e) {
            log.error("限流日志写库失败 appKey={}", appKey, e);
        }
    }

    /** 权限失败日志（PDF 3.5：越权访问数据源的尝试） */
    @Async("logExecutor")
    public void logAuthzFailure(String appKey, String dataSourceId,
                                Boolean authzPassed, String failureReason, String sourceIp) {
        try {
            AuthzFailureLog azfl = new AuthzFailureLog();
            azfl.setAppKey(appKey);
            azfl.setDataSourceId(dataSourceId);
            azfl.setAuthzPassed(authzPassed);
            azfl.setFailureReason(failureReason);
            azfl.setSourceIp(sourceIp);
            authzFailureLogMapper.insert(azfl);
        } catch (Exception e) {
            log.error("权限失败日志写库失败 appKey={}", appKey, e);
        }
    }
}
