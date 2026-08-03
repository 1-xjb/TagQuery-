package com.platform.tagquery.exception;

/**
 * 业务异常 —— 项目中所有"预期内的错误"都抛这个。
 *
 * 为什么要自定义异常？
 * 如果到处直接 return "失败了"，调用方不知道具体原因。
 * 抛 BizException(code=10001)，GlobalExceptionHandler 会自动
 * 拦截并转成统一 JSON 返回，调用方通过 code 就知道是什么错。
 *
 * 使用示例：
 *   throw new BizException(ErrorCode.APP_KEY_INVALID);
 *   throw new BizException(ErrorCode.IDS_EXCEED_LIMIT, "1000");
 */
public class BizException extends RuntimeException {

    private final ErrorCode errorCode;   // 关联的错误码
    private final String formattedMsg;  // 格式化后的最终消息

    /**
     * 不带参数的异常
     * 例：throw new BizException(ErrorCode.IDS_EMPTY);
     * 结果消息 = "用户标识不能为空"
     */
    public BizException(ErrorCode errorCode) {
        super(errorCode.getTemplate());
        this.errorCode = errorCode;
        this.formattedMsg = errorCode.getTemplate();
    }

    /**
     * 带参数的异常（用于有 {0} 占位符的模板）
     * 例：throw new BizException(ErrorCode.IDS_EXCEED_LIMIT, "1000");
     * 结果消息 = "单次查询 ID 数超过上限：1000"
     */
    public BizException(ErrorCode errorCode, Object... args) {
        super(errorCode.format(args));
        this.errorCode = errorCode;
        this.formattedMsg = errorCode.format(args);
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public String getFormattedMsg() {
        return formattedMsg;
    }
}
