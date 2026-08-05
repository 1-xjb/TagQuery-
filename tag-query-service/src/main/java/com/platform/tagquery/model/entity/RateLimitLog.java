package com.platform.tagquery.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("rate_limit_log")
public class RateLimitLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String appKey;

    /** 限流维度：APP_KEY / APP_KEY_DS */
    private String dimension;

    /** 阈值（列名 threshold_val，避开 MySQL 保留字 threshold） */
    private Integer thresholdVal;

    private Integer currentValue;

    private LocalDateTime createdAt;
}
