package com.platform.tagquery.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("app_key_data_source")
public class AppKeyDataSource {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long appKeyId;

    /** Day 2 会由 BIGINT 改为 VARCHAR，先按 Long 写 */
    private Long dataSourceId;

    private LocalDateTime createdAt;
}
