package com.platform.tagquery.model.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.List;

/**
 * 标签查询请求体。
 *
 * 角色：定义调用方必须传什么字段，Spring 收到 JSON 后自动反序列化到这个类，
 *      @Valid 注解触发字段上的校验规则，不合法直接拦截。
 */
@Data
public class TagQueryRequest {

    /** 调用方身份标识（AppKey），用于鉴权第一步 —— 查 app_key 表确认身份存在且已启用 */
    @NotBlank(message = "appKey 不能为空")
    private String appKey;

    /**
     * 请求签名（HMAC-SHA256 计算结果）。
     * 内容 = HMAC(appKey + timestamp + ids.join("|") + dataSourceId, 调用方密钥)
     * 服务端用同样算法算出期望签名，与这个值比对 —— 一致 = 请求没被篡改，不一致 = 拒绝（10002）
     */
    @NotBlank(message = "signature 不能为空")
    private String signature;

    /**
     * 请求时间戳（毫秒）。
     * 服务端校验 Math.abs(当前时间 - timestamp) < 5分钟 —— 通过才放行，
     * 防止攻击者抓包后过几分钟重复发送同一个请求（重放攻击）
     */
    @NotNull(message = "timestamp 不能为空")
    private Long timestamp;

    /** 数据源业务标识（如 ds_lead_scoring_001），指定本次查询在哪个数据源里匹配标签 */
    @NotBlank(message = "dataSourceId 不能为空")
    private String dataSourceId;

    /**
     * 用户标识列表（如加密手机号的 MD5 值）。
     * 单 ID 查就是长度为 1 的列表，批量直接传多个 —— 代码上不需要区分两种场景。
     * 上限 50000 是防极端情况的第一道闸门，真正的业务上限在 Service 层按配置判断。
     */
    @NotEmpty(message = "用户标识不能为空")
    @Size(max = 50000, message = "单次查询 ID 数超过上限：5000")
    private List<String> ids;
}
