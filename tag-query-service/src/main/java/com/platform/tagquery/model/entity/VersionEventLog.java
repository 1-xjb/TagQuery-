package com.platform.tagquery.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("version_event_log")
public class VersionEventLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String dataSourceId;

    /** 操作类型：ACTIVATE / ROLLBACK */
    private String action;

    private String fromVersionKey;

    private String toVersionKey;

    /** 结果：SUCCESS / FAILED */
    private String result;

    private String reason;

    private LocalDateTime createdAt;
}
