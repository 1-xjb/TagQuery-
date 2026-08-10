package com.platform.tagquery.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 权限失败日志 authz_failure_log（PDF 3.5 要求的独立日志类型）。
 * 🔐 记录"越权访问数据源"的尝试：哪个 AppKey 想查哪个没授权的数据源。
 */
@Data
@TableName("authz_failure_log")
public class AuthzFailureLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String appKey;

    /** 越权访问的目标数据源 */
    private String dataSourceId;

    /** 权限校验结果：0=失败 */
    private Boolean authzPassed;

    private String failureReason;

    /** 🔐 来源 IP，安全审计关键 */
    private String sourceIp;

    private LocalDateTime createdAt;
}
