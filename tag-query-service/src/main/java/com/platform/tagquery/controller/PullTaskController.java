package com.platform.tagquery.controller;

import com.platform.tagquery.model.entity.PullTask;
import com.platform.tagquery.service.PullTaskService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 拉取任务 API（Day 5）：手动触发 / 查状态 / 查历史。
 */
@RestController
@RequestMapping("/api/v1/admin/pull-tasks")
public class PullTaskController {

    private final PullTaskService pullTaskService;

    public PullTaskController(PullTaskService pullTaskService) {
        this.pullTaskService = pullTaskService;
    }

    /** 手动触发拉取（不传 partition 则按配置分区规则自动解析） */
    @PostMapping
    public PullTask trigger(@RequestParam Long configId,
                            @RequestParam(required = false) String partition) {
        return pullTaskService.triggerManualPull(configId, partition);
    }

    /** 查任务详情/状态 */
    @GetMapping("/{id}")
    public PullTask get(@PathVariable Long id) {
        return pullTaskService.getById(id);
    }

    /** 历史任务列表 */
    @GetMapping
    public List<PullTask> history() {
        return pullTaskService.listAll();
    }
}
