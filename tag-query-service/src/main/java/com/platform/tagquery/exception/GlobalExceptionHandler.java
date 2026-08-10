package com.platform.tagquery.exception;

import com.platform.tagquery.middleware.RequestIdFilter;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

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
        result.put("requestId", currentRequestId());
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
        result.put("requestId", currentRequestId());

        // 开发阶段打印堆栈，方便排查
        ex.printStackTrace();

        return result;
    }

    /**
     * @Valid 校验失败的统一转换。
     * 📖 为什么单独处理：Spring 校验失败抛的是 MethodArgumentNotValidException，
     *    不处理就会被兜底成 10302 系统异常 —— 调用方会误判为平台故障。
     */

    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    public Map<String, Object> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getAllErrors() != null ? ex.getBindingResult().getAllErrors().get(0).getDefaultMessage() : "校验失败";
        ErrorCode code = ErrorCode.PARAM_FORMAT_INVALID; // 10103
        if(msg.contains("用户标识不能为空")) code = ErrorCode.IDS_EMPTY;
        else if(msg.contains("超过上限")) code = ErrorCode.IDS_EXCEED_LIMIT;

        Map<String , Object> result = new LinkedHashMap<>();
        result.put("code" , code.getCode());
        result.put("message", code.equals(ErrorCode.IDS_EXCEED_LIMIT)
                ? code.format("5000") : code.getTemplate());
        result.put("requestId", currentRequestId());
        return result;
    }

    /**
     * 取当前请求的 requestId（Day 4 由 RequestIdFilter 塞入请求属性）。
     * 在 @Async 线程或非 Web 环境里拿不到请求上下文，返回 null。
     */
    private String currentRequestId() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                Object rid = attrs.getRequest().getAttribute(RequestIdFilter.ATTR_REQUEST_ID);
                if (rid != null) {
                    return rid.toString();
                }
            }
        } catch (Exception ignored) {
            // 拿不到就返回 null，不影响异常处理本身
        }
        return null;
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public Map<String, Object> handleNotReadable(
            org.springframework.http.converter.HttpMessageNotReadableException ex) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", ErrorCode.PARAM_FORMAT_INVALID.getCode());
        result.put("message", ErrorCode.PARAM_FORMAT_INVALID.getTemplate());
        result.put("requestId", currentRequestId());
        return result;
    }
}
