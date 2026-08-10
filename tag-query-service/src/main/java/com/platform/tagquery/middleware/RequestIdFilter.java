package com.platform.tagquery.middleware;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;

@Component
public class RequestIdFilter extends OncePerRequestFilter {
    public static final String ATTR_REQUEST_ID = "requestId";
    /** 来源 IP 请求属性 key（AuthService/TagQueryService 从请求属性取真实 IP） */
    public static final String ATTR_SOURCE_IP = "sourceIp";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String requestId = request.getHeader("x-request-id");
        if(requestId == null || requestId.isBlank()){
            requestId = "req_" + System.currentTimeMillis() +
                    "_" + UUID.randomUUID().toString().substring(0, 8);
        }
        request.setAttribute(ATTR_REQUEST_ID, requestId);
        request.setAttribute(ATTR_SOURCE_IP, getClientIp(request));
        // 响应头带回去，调用方可用它关联日志
        response.setHeader("X-Request-Id", requestId);
        MDC.put(ATTR_REQUEST_ID, requestId);
        try{
            chain.doFilter(request, response);
        }finally {
            MDC.remove(ATTR_REQUEST_ID);
        }
    }

    /** 取真实来源 IP：优先 X-Forwarded-For（网关/反代场景，取第一个），否则连接远端地址。 */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip)) {
            return ip.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
