package com.platform.tagquery.service;


import com.platform.tagquery.exception.BizException;
import com.platform.tagquery.exception.ErrorCode;
import com.platform.tagquery.metrics.QueryMetrics;
import com.platform.tagquery.middleware.RequestIdFilter;
import com.platform.tagquery.model.dto.TagQueryRequest;
import com.platform.tagquery.model.dto.TagQueryResponse;
import com.platform.tagquery.model.entity.AppKey;
import com.platform.tagquery.repository.redis.TagRedisRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 标签查询核心 —— 把 PDF 查询链路 7 步串成一条线。
 *
 * 📖 编排顺序 = PDF 原文顺序：鉴权(②③) → 授权(③) → 限流(④) → 查生效版本(⑤)
 *    → 批量查(⑤) → 组装返回(⑥) → 异步日志(⑦，Day 4 加)。
 */

@Service
public class TagQueryService {

    /** 单次批量上限：配置文件注入，压测后调整（PDF：上限待压测确认） */
    @Value("${tag.query.max-batch-size:1000}")
    private Integer maxBatchSize;

    private final AuthService authService;
    private final RateLimitService rateLimitService;
    private final TagRedisRepository tagRedis;
    private final LogService logService;
    private final QueryMetrics queryMetrics;

    public TagQueryService(AuthService authService, RateLimitService rateLimitService,
                           TagRedisRepository tagRedis, LogService logService, QueryMetrics queryMetrics) {
        this.authService = authService;
        this.rateLimitService = rateLimitService;
        this.tagRedis = tagRedis;
        this.logService = logService;
        this.queryMetrics = queryMetrics;
    }

    public TagQueryResponse queryTags(TagQueryRequest request){
        long start = System.currentTimeMillis();
        String requestId = currentRequestId();

        AppKey caller = authService.authenticate(
                request.getAppKey(),
                request.getTimestamp(),
                request.getIds(),
                request.getDataSourceId(),
                request.getSignature()
        );

        if(request.getIds().size() > maxBatchSize){
            throw new BizException(ErrorCode.IDS_EXCEED_LIMIT, maxBatchSize);
        }

        try {
            rateLimitService.tryAcquire(caller.getAppKey(), caller.getQpsLimit());
        } catch (BizException e) {
            // 限流命中也埋点（Day 8）
            if (e.getErrorCode() == ErrorCode.RATE_LIMITED) {
                queryMetrics.recordRateLimited(caller.getAppKey());
            }
            throw e;
        }

        String versionKey = tagRedis.getActiveVersion(request.getDataSourceId());
        Map<String, Set<String>> tagMap;

        if(versionKey == null){
            tagMap = Map.of();
        }else{
            // ---- ⑤ 带超时/降级保护地批量查询（Day 4）----
            tagMap = queryWithGuard(caller.getAppKey(), request.getDataSourceId(), versionKey, request.getIds());
        }

        // ---- ⑥ 组装响应 ----

        List<TagQueryResponse.IdTags> data = new ArrayList<>(
                request.getIds().size()
        );

        int hitCount = 0;
        for(String id : request.getIds()){
            Set<String> tags = tagMap.getOrDefault(id,Set.of());
            if(!tags.isEmpty()){
                hitCount++;
            }

            data.add(new TagQueryResponse.IdTags(id,new ArrayList<>(tags)));

        }

        long costMs = System.currentTimeMillis() - start;

        TagQueryResponse resp = new TagQueryResponse();
        resp.setCode(ErrorCode.SUCCESS.getCode());
        resp.setMessage(ErrorCode.SUCCESS.getTemplate());
        resp.setRequestId(requestId);
        resp.setData(data);
        resp.setVersionId(versionKey);
        resp.setStats(new TagQueryResponse.Stats(
                request.getIds().size(),hitCount,
                request.getIds().isEmpty() ? 0.0 : (double) hitCount / request.getIds().size(),
                costMs
        ));

        // ---- ⑦ 异步写查询日志（Day 4；@Async 不阻塞主链路）----
        logService.logQuery(requestId, caller.getAppKey(), request.getDataSourceId(),
                request.getIds().size(), hitCount, costMs, "SUCCESS", versionKey, null);

        // ---- ⑧ 监控埋点（Day 8）----
        queryMetrics.recordQuery(caller.getAppKey(), "SUCCESS", costMs);
        queryMetrics.recordIds(caller.getAppKey(), request.getIds().size(), hitCount);   // 命中率

        return resp;
    }

    /**
     * 查询 + 降级保护（Day 4）。
     *
     * 🔐/⚡ 双重目的：
     * 1. Redis 挂了 → 立刻返回 10301 而不是让 Tomcat 线程全部卡死（故障隔离，熔断雏形）；
     * 2. 慢查询超过阈值 → 返回 10202，把线程还给连接池（保其他调用方，PDF：互不影响）。
     */
    private Map<String, Set<String>> queryWithGuard(String appKey, String dsId, String version, List<String> ids) {
        if (!tagRedis.ping()) {
            throw new BizException(ErrorCode.REDIS_UNAVAILABLE);      // 10301
        }
        try {
            return tagRedis.batchGetTags(dsId, version, ids);
        } catch (RedisConnectionFailureException e) {
            throw new BizException(ErrorCode.REDIS_UNAVAILABLE);      // 连接断 → 10301
        } catch (QueryTimeoutException e) {
            queryMetrics.recordTimeout(appKey);                       // 超时率埋点
            throw new BizException(ErrorCode.QUERY_TIMEOUT);          // 超时 → 10202
        }
    }

    /** 取当前请求的 requestId（由 RequestIdFilter 塞入请求属性） */
    private String currentRequestId() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                Object rid = attrs.getRequest().getAttribute(RequestIdFilter.ATTR_REQUEST_ID);
                if (rid != null) {
                    return rid.toString();
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
