package com.platform.tagquery.exception;

import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 全局异常处理器 —— 项目里所有抛出的异常最终都来这里。
 *
 * 为什么需要它？
 * 假设 AuthService 里抛了 BizException，如果不处理，
 * 用户会看到 Tomcat 的 500 错误页（一堆 HTML）。
 * 有了这个类，所有 BizException 会被拦截，转成统一的 JSON 返回：
 *
 * {
 *   "code": 10001,
 *   "message": "调用身份无效...",
 *   "requestId": "req_xxx"
 * }
 *
 * @RestControllerAdvice = @ControllerAdvice + @ResponseBody
 * 意思是：全局拦截异常，返回 JSON
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理我们自己的 BizException。
     *
     * @ExceptionHandler(BizException.class) 告诉 Spring：
     * "如果有任何地方抛了 BizException，来这个方法处理"
     */
    @ExceptionHandler(BizException.class)
    public Map<String, Object> handleBizException(BizException ex) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", ex.getErrorCode().getCode());
        result.put("message", ex.getFormattedMsg());
        result.put("requestId", null); // Day 4 才实现 requestId，现在填 null
        return result;
    }

    /**
     * 处理意料之外的异常（兜底）。
     * 比如空指针、数据库连接失败等没预料到的错误。
     */
    @ExceptionHandler(Exception.class)
    public Map<String, Object> handleException(Exception ex) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", ErrorCode.SYSTEM_ERROR.getCode());
        result.put("message", ErrorCode.SYSTEM_ERROR.getTemplate());
        result.put("requestId", null);

        // 开发阶段打印堆栈，方便排查
        ex.printStackTrace();

        return result;
    }
}
