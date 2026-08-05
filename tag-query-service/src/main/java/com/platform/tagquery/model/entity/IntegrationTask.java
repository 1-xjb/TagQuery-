package com.platform.tagquery.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("integration_task")
public class IntegrationTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long pullTaskId;

    private String dataSourceId;

    /** 状态：PENDING / PARSING / VALIDATING / LOADING / SUCCESS / FAILED */
    private String status;

    private Long totalRecords;

    private Long validRecords;

    /** 重复率，浮点用 BigDecimal 保证精度 */
    private BigDecimal duplicateRate;

    /** 空值率 */
    private BigDecimal nullRate;

    private String failureReason;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    private LocalDateTime createdAt;
}
