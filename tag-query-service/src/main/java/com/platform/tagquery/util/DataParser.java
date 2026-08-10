package com.platform.tagquery.util;

import java.util.ArrayList;
import java.util.List;

/**
 * 标签文件行解析器（Day 6）。
 * 格式（PDF 3.3）：用户标识|标识类型|Code1|Code2|...|
 *
 * 📖 为什么用 split("\\|", -1)：
 *    - "|" 是正则特殊字符，必须转义；
 *    - 第二参数 -1 保留尾部空串（"a|PN|" 拆成 3 段而不是 2 段），
 *      否则"有 ID 没标签"的行会被误判成格式错误。
 *
 * 🔐 安全：输入来自客户文件，属不可信数据。解析器对每个字段做长度/字符白名单检查，
 *    防止脏数据（超长串、控制字符）灌进 Redis 撑爆内存或污染查询结果。
 */
public class DataParser {

    private static final int MAX_FIELD_LEN = 128;
    private static final int MAX_TAGS_PER_ID = 500;

    /** 解析结果：null 表示格式非法（调用方计入 invalidFormat） */
    public static ParsedRecord parseLine(String line, int lineNumber) {
        if (line == null || line.isBlank()) {
            return null;
        }
        String[] parts = line.split("\\|", -1);
        if (parts.length < 3) {                      // 至少：id + 类型 + 1 个 Code
            return null;
        }
        String userId = parts[0].trim();
        String idType = parts[1].trim();
        if (userId.isEmpty() || userId.length() > MAX_FIELD_LEN) {
            return null;
        }
        List<String> tags = new ArrayList<>();
        for (int i = 2; i < parts.length; i++) {
            String code = parts[i].trim();
            if (!code.isEmpty()) {
                if (code.length() > MAX_FIELD_LEN || tags.size() >= MAX_TAGS_PER_ID) {
                    return null;                     // 🔐 超限直接判非法行
                }
                tags.add(code);
            }
        }
        return new ParsedRecord(userId, idType, tags, lineNumber);
    }

    /** 简单值对象（不用 Lombok，纯工具内聚） */
    public static class ParsedRecord {
        public final String userId;
        public final String idType;
        public final List<String> tagCodes;
        public final int lineNumber;

        public ParsedRecord(String userId, String idType, List<String> tagCodes, int lineNumber) {
            this.userId = userId;
            this.idType = idType;
            this.tagCodes = tagCodes;
            this.lineNumber = lineNumber;
        }
    }
}
