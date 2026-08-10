package com.platform.tagquery.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 日志线程池（Day 4）：核心 2 / 最大 8 / 队列 500 / 满了由调用线程自己执行（CallerRunsPolicy）。
 * 📖 拒绝策略选 CallerRuns 而不是 Discard：宁可让查询线程偶尔自己写一次日志（略慢），
 *    也不丢日志 —— 同时形成反压，队列满时自然限制日志产生速度。
 */
@Configuration
public class AsyncConfig {

    @Bean("logExecutor")
    public Executor logExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("log-async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
