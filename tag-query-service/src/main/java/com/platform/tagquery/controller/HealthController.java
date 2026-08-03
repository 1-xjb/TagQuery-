package com.platform.tagquery.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康检查接口 —— 最简单的 Controller 示例。
 *
 * 理解 Controller 的三要素：
 * 1. @RestController    → 告诉 Spring "我是一个 Controller，返回 JSON"
 * 2. @GetMapping("/xxx") → 告诉 Spring "GET 请求 /xxx 时调用这个方法"
 * 3. 方法返回值          → Spring 自动转成 JSON 返回给调用方
 *
 * 测试方法（项目启动后）：
 * 在浏览器访问 http://localhost:8080/api/v1/health
 * 应该看到 {"status": "UP", "service": "tag-query-service"}
 */
@RestController
public class HealthController {

    @GetMapping("/api/v1/health")
    public Map<String, String> health() {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("status", "UP");
        result.put("service", "tag-query-service");
        return result;
    }
}
