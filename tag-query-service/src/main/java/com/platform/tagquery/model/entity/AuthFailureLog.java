package com.platform.tagquery.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("auth_failure_log")
public class AuthFailureLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 可能是伪造的 AppKey，允许 NULL */
    private String appKey;

    private Boolean signaturePassed;

    private Boolean timestampPassed;

    private String failureReason;

    private String sourceIp;

    private LocalDateTime createdAt;
}
