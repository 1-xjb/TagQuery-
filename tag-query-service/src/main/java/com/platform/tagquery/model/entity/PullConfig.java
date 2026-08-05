package com.platform.tagquery.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("pull_config")
public class PullConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String customerName;

    private String dataSourceId;

    private String s3Endpoint;

    private String s3Bucket;

    private String s3Prefix;

    private String cronExpression;

    /** 分区规则：LATEST / CURRENT_MONTH */
    private String partitionRule;

    /** 0=停用, 1=启用 */
    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
