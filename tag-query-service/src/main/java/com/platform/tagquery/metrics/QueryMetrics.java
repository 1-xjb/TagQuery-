package com.platform.tagquery.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 业务指标埋点（Day 8，Micrometer → Prometheus）。
 *
 * ⚡ 性能：Counter/Timer 都是内存原子操作，纳秒级开销，主链路随便打。
 *    但 ⚠️ 千万别把 id/手机号当 tag —— 高基数标签会撑爆 Prometheus。
 */
@Component
public class QueryMetrics {

    private final MeterRegistry registry;

    public QueryMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /** 记录一次查询：QPS + RT（P99 由 Prometheus 的 histogram_quantile 算） */
    public void recordQuery(String appKey, String status, long costMs) {
        Counter.builder("tag_query_requests_total")
                .tag("appKey", appKey).tag("status", status)
                .register(registry).increment();
        Timer.builder("tag_query_latency")
                .tag("appKey", appKey)
                .publishPercentileHistogram()
                .register(registry)
                .record(Duration.ofMillis(costMs));
    }

    public void recordRateLimited(String appKey) {
        Counter.builder("tag_query_rate_limited_total")
                .tag("appKey", appKey).register(registry).increment();
    }

    /** 命中率（PDF 3.5）：总查询 ID 数 / 命中 ID 数。命中率 = hits/ids，Grafana 查询算。 */
    public void recordIds(String appKey, int totalIds, int hitIds) {
        Counter.builder("tag_query_ids_total")
                .tag("appKey", appKey).register(registry).increment(totalIds);
        Counter.builder("tag_query_hits_total")
                .tag("appKey", appKey).register(registry).increment(hitIds);
    }

    /** 超时率：查询超时的请求数 */
    public void recordTimeout(String appKey) {
        Counter.builder("tag_query_timeout_total")
                .tag("appKey", appKey).register(registry).increment();
    }

    /** 拉取成功率：status=PULLED/FAILED（成功率 = PULLED/total） */
    public void recordPull(String status) {
        Counter.builder("tag_pull_total")
                .tag("status", status).register(registry).increment();
    }

    /** 集成成功率：status=SUCCESS/FAILED（成功率 = SUCCESS/total） */
    public void recordIntegration(String status) {
        Counter.builder("tag_integration_total")
                .tag("status", status).register(registry).increment();
    }
}
