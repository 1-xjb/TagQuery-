package com.platform.tagquery.controller;

import com.platform.tagquery.model.entity.PullConfig;
import com.platform.tagquery.service.PullConfigService;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 拉取配置管理 API（Day 5，平台内部运维使用，部署时网关层限制内网访问）。
 */
@RestController
@RequestMapping("/api/v1/admin/pull-configs")
public class PullConfigController {

    private final PullConfigService pullConfigService;

    public PullConfigController(PullConfigService pullConfigService) {
        this.pullConfigService = pullConfigService;
    }

    @GetMapping
    public List<PullConfig> list() {
        return pullConfigService.listAll();
    }

    @GetMapping("/{id}")
    public PullConfig get(@PathVariable Long id) {
        return pullConfigService.getById(id);
    }

    @PostMapping
    public PullConfig create(@RequestBody PullConfig config) {
        return pullConfigService.create(config);
    }

    @PutMapping("/{id}")
    public PullConfig update(@PathVariable Long id, @RequestBody PullConfig config) {
        config.setId(id);
        return pullConfigService.update(config);
    }

    @PatchMapping("/{id}/status")
    public Map<String, Object> toggle(@PathVariable Long id, @RequestParam boolean enable) {
        pullConfigService.toggleStatus(id, enable);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 0);
        result.put("message", "success");
        return result;
    }
}
