package com.platform.tagquery.model.dto;

import lombok.Data;

import java.util.List;

/**
 * 标签查询响应体。
 *
 * 角色：Controller 组装好这个对象，Spring 自动序列化成 JSON 返回给调用方。
 *      code + message + requestId 三件套永远存在，调用方解析逻辑可以写死。
 */
@Data
public class TagQueryResponse {

    /** 错误码，0 = 成功，其余值见 ErrorCode 枚举（10001~10302） */
    private Integer code;

    /** 错误消息，成功时固定返回 "success"，失败时返回对应的中文提示 */
    private String message;

    /** 请求唯一标识（requestId），串联本次请求的全部日志，排查问题时 grep 这个 id 即可 */
    private String requestId;

    /**
     * 本次查询命中的数据版本（如 v_ds_001_20260725_01）。
     * PDF 要求：返回结果需可追溯到查询时的数据版本 —— 出问题时靠它定位是哪一版数据的问题
     */
    private String versionId;

    /** 查询结果主体：数组中每个元素是一个 ID 对应的标签列表 */
    private List<IdTags> data;

    /** 统计摘要：总量/命中量/命中率/耗时，供调用方评估调用效果 */
    private Stats stats;

    /**
     * 单个 ID 的查询结果。
     * 写在 TagQueryResponse 内部是因为它只在这里出现，不收成外部类避免目录散乱。
     */
    @Data
    public static class IdTags {

        /** 用户标识（对应请求里 ids 数组中的某个值） */
        private String id;

        /** 该用户命中的标签 Code 列表，未命中时为空数组 []——不是系统错误，是正常结果 */
        private List<String> tags;

        public IdTags(String id, List<String> tags) {
            this.id = id;
            this.tags = tags;
        }
    }

    /**
     * 查询统计信息。
     * 命中率是 PDF 要求的监控指标之一，每次查询直接带回去，调用方也能看。
     */
    @Data
    public static class Stats {

        /** 本次查询的 ID 总数 */
        private Integer totalIds;

        /** 命中了至少一个标签的 ID 数量 */
        private Integer hitIds;

        /** 命中率 = hitIds / totalIds */
        private Double hitRate;

        /** 查询耗时（毫秒），从接受请求到组装完响应的全部时间 */
        private Long costMs;

        public Stats(Integer totalIds, Integer hitIds, Double hitRate, Long costMs) {
            this.totalIds = totalIds;
            this.hitIds = hitIds;
            this.hitRate = hitRate;
            this.costMs = costMs;
        }
    }
}
