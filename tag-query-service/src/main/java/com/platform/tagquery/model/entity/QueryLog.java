package com.platform.tagquery.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("query_log")
public class QueryLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String requestId;

    private String appKey;

    private String dataSourceId;

    private Integer idCount;

    private Integer hitCount;

    private Long costMs;

    private String status;

    private String versionKey;

    private String sourceIp;

    private LocalDateTime createdAt;
}
