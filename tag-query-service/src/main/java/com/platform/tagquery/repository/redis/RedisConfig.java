package com.platform.tagquery.repository.redis;

/**
 * Redis 配置。
 *
 * 📖 为什么这么写：
 * 1. 全项目只用 StringRedisTemplate（key 和 value 都是字符串）。
 *    标签 Code、ID、版本号全是字符串，用不着 JDK 序列化 ——
 *    JDK 序列化出来的二进制不可读、跨语言不认，用 redis-cli 排查时一团乱码。
 * 2. Spring Boot 默认已经自动配置了 StringRedisTemplate，这里显式声明一次，
 *    是为了以后改序列化器/超时只动这一个文件。
 *
 * ⚡ 性能：连接池参数已在 application.yml 配好（max-active=50）。
 *    QPS 1000 时每次 Redis 操作要控制在毫秒级，连接不够会排队，50 是开发期够用的值。
 */


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class RedisConfig {

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
        return new StringRedisTemplate(factory);
    }
}
