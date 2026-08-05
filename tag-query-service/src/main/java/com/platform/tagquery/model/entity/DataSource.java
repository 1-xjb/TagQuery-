package com.platform.tagquery.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("data_source")
public class DataSource {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业务标识，如 ds_lead_scoring_001 */
    private String sourceId;

    private String sourceName;

    private String customerName;

    /** 0=停用, 1=启用 */
    private Integer status;

    private LocalDateTime createdAt;
}
