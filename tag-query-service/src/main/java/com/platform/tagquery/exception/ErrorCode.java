package com.platform.tagquery.exception;

import java.text.MessageFormat;

/**
 * 错误码枚举 —— 整个项目所有错误码的唯一定义处。
 *
 * 为什么要用枚举而不用写死的数字？
 * 1. 一处定义，全局引用，不会写错数字
 * 2. 每个错误码自带中文模板，方便 format 动态填充参数
 * 3. IDEA 能自动提示所有错误码
 *
 * 命名规则：大写下划线 = 常量，如 APP_KEY_INVALID
 * 数值分段：
 *   10001-10099 = 鉴权相关
 *   10101-10199 = 参数校验
 *   10201-10299 = 限流 & 超时
 *   10301-10399 = 系统异常
 */
public enum ErrorCode {

    // --- 鉴权相关 (10001-10099) ---
    APP_KEY_INVALID(10001, "调用身份无效，请确认 AppKey 是否正确或是否已启用"),
    SIGNATURE_INVALID(10002, "签名校验失败，请检查签名生成规则"),
    TIMESTAMP_EXPIRED(10003, "请求已过期，请重新发起请求"),
    DATA_SOURCE_UNAUTHORIZED(10004, "以下为无效数据源：{0}"),

    // --- 参数校验 (10101-10199) ---
    IDS_EMPTY(10101, "用户标识不能为空"),
    IDS_EXCEED_LIMIT(10102, "单次查询 ID 数超过上限：{0}"),
    PARAM_FORMAT_INVALID(10103, "输入参数格式不符合接口要求"),
    DATA_SOURCE_INVALID(10104, "数据源标识无效：{0}"),

    // --- 限流 & 超时 (10201-10299) ---
    RATE_LIMITED(10201, "当前任务配置已达到调用限制，请稍后重试"),
    QUERY_TIMEOUT(10202, "查询超时，请稍后重试"),

    // --- 系统异常 (10301-10399) ---
    REDIS_UNAVAILABLE(10301, "实时查询服务暂不可用，请稍后重试"),
    SYSTEM_ERROR(10302, "系统异常，请联系平台服务方排查"),

    // --- 成功 ---
    SUCCESS(0, "success");

    // ==================== 枚举的结构 ====================
    // 每个枚举值就是上面定义的那些（如 APP_KEY_INVALID），
    // 每个枚举值有两个属性：code（数字）和 template（中文模板）

    private final int code;       // 错误码数字
    private final String template; // 错误消息模板（{0} {1} 是占位符）

    // 枚举的构造方法必须是 private
    ErrorCode(int code, String template) {
        this.code = code;
        this.template = template;
    }

    // --- 公开方法 ---

    public int getCode() {
        return code;
    }

    public String getTemplate() {
        return template;
    }

    /**
     * 格式化错误消息，替换模板中的占位符 {0} {1} ...
     *
     * 例子：
     *   ErrorCode.IDS_EXCEED_LIMIT.format("1000")
     *   → "单次查询 ID 数超过上限：1000"
     */
    public String format(Object... args) {
        return MessageFormat.format(template, args);
    }
}
