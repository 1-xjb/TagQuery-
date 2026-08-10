package com.platform.tagquery.service;


import com.platform.tagquery.exception.BizException;
import com.platform.tagquery.exception.ErrorCode;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * 限流服务 —— 查询链路第 4 步（QPS 控制）。
 *
 * 📖 算法选择：滑动窗口（ZSET 实现）。
 *    - 固定窗口（每分钟清零计数）在窗口边界会放进 2 倍流量，不合格；
 *    - 滑动窗口记录每个请求的时间戳，精确统计"最近 1 秒"的请求数。
 *
 * 🔐 安全：限流是防刷的第一道闸门。没有它，一个恶意/出 bug 的调用方
 *    可以打满 Redis 连接，拖垮所有客户（PDF：单个调用方异常不得影响其他调用方）。
 *
 * ⚡ 性能关键：整个"清理过期→计数→写入"必须用 Lua 脚本在 Redis 内原子完成。
 *    如果拆成多条命令用 Java 发，高并发下两个请求会同时读到旧计数 → 限流失效（超放）。
 *    Lua 脚本在 Redis 单线程里整体执行，天然无并发问题，还只要 1 次网络往返。
 */

@Service
public class RateLimitService {
    private static final String LUA_SLIDING_WINDOW =
            "redis.call('ZREMRANGEBYSCORE',KEYS[1],0,ARGV[1]-ARGV[2])" +
            "local cnt = redis.call('ZCARD',KEYS[1])" +
            "if cnt < tonumber(ARGV[3]) then" +
            " redis.call('ZADD',KEYS[1],ARGV[1],ARGV[1] .. math.random())" +
            " redis.call('PEXPIRE',KEYS[1],ARGV[2])" +
            " return 1" +
            " else " +
            " return 0 " +
            "end";
    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> script;
    private final LogService logService;

    public RateLimitService(StringRedisTemplate redisTemplate, LogService logService){
        this.redisTemplate = redisTemplate;
        this.script = new DefaultRedisScript<>(LUA_SLIDING_WINDOW, Long.class);
        this.logService = logService;
    }

    /**
     * 尝试获取许可。不通过直接抛 10201。
     *
     * @param appKey   调用方
     * @param maxQps   该调用方的 QPS 上限（来自 app_key.qps_limit，配置化而不是写死）
     *
     * 📖 限流维度：按 appKey 独立限流 —— 每个调用方一个桶，互不影响（PDF 要求）。
     */

    public void tryAcquire(String appKey , int maxQps){
        String key =  "ratelimit:qps:" + appKey;
        Long allowed = redisTemplate.execute(script,
                Collections.singletonList(key),
                String.valueOf(System.currentTimeMillis()),"1000",
                String.valueOf(maxQps));

        if (allowed == null || allowed == 0L){
            // 记录限流日志（PDF 3.5：AppKey/限流维度/阈值/当前值）
            Long current = redisTemplate.opsForZSet().zCard(key);
            logService.logRateLimit(appKey, "APP_KEY", maxQps, current == null ? 0 : current.intValue());
            throw new BizException(ErrorCode.RATE_LIMITED);
        }
    }
}
