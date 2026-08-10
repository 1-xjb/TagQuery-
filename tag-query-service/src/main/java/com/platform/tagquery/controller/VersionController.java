package com.platform.tagquery.controller;

import com.platform.tagquery.model.entity.DataVersion;
import com.platform.tagquery.service.VersionService;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 版本管理 API（Day 7）：列表 / 生效 / 回退。
 */
@RestController
@RequestMapping("/api/v1/admin/versions")
public class VersionController {

    private final VersionService versionService;

    public VersionController(VersionService versionService) {
        this.versionService = versionService;
    }

    /** 某数据源的版本列表（倒序） */
    @GetMapping
    public List<DataVersion> list(@RequestParam String dataSourceId) {
        return versionService.listVersions(dataSourceId);
    }

    /** 生效指定版本 */
    @PostMapping("/{id}/activate")
    public Map<String, Object> activate(@PathVariable Long id,
                                        @RequestParam(required = false) String reason) {
        versionService.activateVersion(id, reason);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 0);
        result.put("message", "success");
        return result;
    }

    /** 回退到该数据源上一可用版本 */
    @PostMapping("/rollback")
    public Map<String, Object> rollback(@RequestParam String dataSourceId,
                                        @RequestParam(required = false) String reason) {
        versionService.rollbackVersion(dataSourceId, reason);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 0);
        result.put("message", "success");
        return result;
    }
}
