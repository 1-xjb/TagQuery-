package com.platform.tagquery.scheduler;

import com.platform.tagquery.model.entity.PullConfig;
import com.platform.tagquery.service.PullConfigService;
import com.platform.tagquery.service.PullTaskService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 定时拉取调度（Day 8）：每分钟扫一次启用配置，cron 到点就触发。
 *
 * 📖 为什么用 Redis 分布式锁（SET NX EX）而不是单机 @Scheduled：
 *    生产至少 2 个实例，没锁会同一时刻两个实例各拉一遍 → 重复任务、重复灌库。
 *    SET key value NX EX 55：不存在才设置成功（抢锁），55 秒自动过期（防持锁实例宕机死锁）。
 */
@Component
public class PullTaskScheduler {

    private static final String LOCK_KEY = "lock:pull_scheduler";
    private static final Duration LOCK_TTL = Duration.ofSeconds(55);

    private final StringRedisTemplate redisTemplate;
    private final PullConfigService pullConfigService;
    private final PullTaskService pullTaskService;
    private final String instanceId = UUID.randomUUID().toString();

    public PullTaskScheduler(StringRedisTemplate redisTemplate,
                             PullConfigService pullConfigService,
                             PullTaskService pullTaskService) {
        this.redisTemplate = redisTemplate;
        this.pullConfigService = pullConfigService;
        this.pullTaskService = pullTaskService;
    }

    @Scheduled(cron = "0 * * * * ?")   // 每分钟整
    public void scanAndTrigger() {
        Boolean gotLock = redisTemplate.opsForValue()
                .setIfAbsent(LOCK_KEY, instanceId, LOCK_TTL);
        if (!Boolean.TRUE.equals(gotLock)) {
            return;   // 别的实例在跑，本轮跳过 —— 这很正常，不是错误
        }
        try {
            LocalDateTime now = LocalDateTime.now();
            List<PullConfig> configs = pullConfigService.getEnabledConfigs();
            for (PullConfig config : configs) {
                if (isDue(config, now)) {
                    try {
                        // 📖 单个配置失败不拖垮整轮扫描；失败告警在 trigger 内部发
                        pullTaskService.triggerScheduledPull(config.getId());
                    } catch (Exception ignored) {
                    }
                }
            }
        } finally {
            // 📖 开发期不主动删锁，等 55 秒自然过期即可（生产建议补 Lua 校验删除）
        }
    }

    /** cron 是否命中当前这一分钟 */
    private boolean isDue(PullConfig config, LocalDateTime now) {
        try {
            CronExpression expr = CronExpression.parse(config.getCronExpression());
            LocalDateTime next = expr.next(now.minusMinutes(1));
            if (next == null) {
                return false;
            }
            LocalDateTime minuteStart = now.withSecond(0).withNano(0);
            return !next.isBefore(minuteStart) && next.isBefore(minuteStart.plusMinutes(1));
        } catch (Exception e) {
            return false;   // cron 表达式非法 → 不调度，配置管理 API 里应校验
        }
    }
}
