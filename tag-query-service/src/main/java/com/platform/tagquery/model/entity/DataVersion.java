package com.platform.tagquery.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("data_version")
public class DataVersion {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 版本标识，如 v_ds_001_20260725_01 */
    private String versionKey;

    private String dataSourceId;

    private Long integrationTaskId;

    private Long recordCount;

    /** 校验状态：PENDING / PASSED / FAILED */
    private String validateStatus;

    /** 生效状态：INACTIVE / ACTIVE / DEPRECATED */
    private String activeStatus;

    private String validateFailureReason;

    private LocalDateTime activatedAt;

    private LocalDateTime createdAt;
}
