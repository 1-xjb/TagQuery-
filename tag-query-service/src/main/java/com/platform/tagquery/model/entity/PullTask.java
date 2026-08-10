package com.platform.tagquery.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("pull_task")
public class PullTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long pullConfigId;

    private String s3FullPath;

    /** 目标分区（对应列 partition_name —— partition 是 MySQL 8.0+ 保留字，V1 已改名） */
    private String partitionName;

    /** 触发方式：SCHEDULED / MANUAL */
    private String triggerType;

    /** 状态：PENDING / PULLING / PULLED / FAILED */
    private String status;

    private Integer fileCount;

    private Long fileTotalSize;

    private String failureReason;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    /** Day 6 V4 迁移加列，届时补上 */
    private String dataSourceId;
}
