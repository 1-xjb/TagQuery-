package com.platform.tagquery.controller;


import com.platform.tagquery.model.dto.TagQueryRequest;
import com.platform.tagquery.model.dto.TagQueryResponse;
import com.platform.tagquery.service.TagQueryService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 实时标签查询 API —— 对外唯一查询入口。
 *
 * 📖 @Valid 触发请求体上的校验注解：ids 空 → 10101，超 5000 → 10102，
 *    字段缺失/格式错 → 10103（Day 4 在 GlobalExceptionHandler 补统一转换）。
 */


@RestController
@RequestMapping("/api/v1/tag")
@Validated
public class TagQueryController {
    public final TagQueryService tagQueryService;

    public TagQueryController(TagQueryService tagQueryService) {
        this.tagQueryService = tagQueryService;
    }

    @PostMapping("/query")
    public TagQueryResponse query(@RequestBody @Validated TagQueryRequest request) {
        return tagQueryService.queryTags(request);
    }
}
