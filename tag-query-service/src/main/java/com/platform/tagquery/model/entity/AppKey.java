package com.platform.tagquery.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 调用方身份表 app_key 的实体类。
 *
 * 📖 为什么这么写：
 * 1. @Data 是 Lombok 注解，自动生成 getter/setter/toString，省掉几十行样板代码。
 *    （pom.xml 里已经引入 lombok，实体类都靠它）
 * 2. @TableName("app_key") 告诉 MyBatis-Plus：这个类对应数据库哪张表。
 * 3. @TableId(type = IdType.AUTO) 告诉它：主键 id 是数据库自增的，插入时不用我们赋值。
 * 4. 字段用驼峰命名（appKey），MyBatis-Plus 按 application.yml 里的
 *    map-underscore-to-camel-case: true 自动映射到 app_key 列。
 *
 * 🔐 安全：appSecret 是签名密钥，属于敏感数据。
 *    - 任何日志里禁止打印（Lombok 的 toString 会带上它，所以本类加 @ToString.Exclude）。
 *    - 管理 API 返回时也绝不能带出（Day 2 的 DTO 会刻意不含此字段）。
 */
@Data
@TableName("app_key")
public class AppKey {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String appKey;

    private String appName;

    @com.fasterxml.jackson.annotation.JsonIgnore   // 🔐 序列化成 JSON 时直接忽略，双保险
    @lombok.ToString.Exclude                        // 🔐 toString 不含密钥，防止日志泄露
    private String appSecret;

    /** 0=停用, 1=启用 */
    private Integer status;

    /** 🔐 该调用方的 QPS 上限（V2 迁移加列）。限流配置跟着身份走，而不是写死在代码里。 */
    private Integer qpsLimit;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}