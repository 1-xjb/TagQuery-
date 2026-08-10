package com.platform.tagquery.repository.redis;

import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 标签数据的 Redis 读写 —— 项目的数据心脏。
 *
 * 📖 Key 设计（与蓝图 5.2 节一致）：
 *    tag:{dataSourceId}:{versionKey}:{id}  →  Set<标签Code>   某 ID 在某版本的标签
 *    tag:active_version:{dataSourceId}     →  String 版本号    生效版本指针
 *
 * 📖 为什么 key 里带 versionKey —— 这是"全量替换 + 秒级切换 + 可回退"的关键：
 *    新版本数据写到新 key 空间，和旧版本井水不犯河水；
 *    切换 = 改一个指针 key（原子操作，查询方无感知）；
 *    回退 = 指针改回去。旧数据异步删，查询永不中断。
 * 🔐 安全：key 前缀含 dataSourceId，不同数据源的数据在 Redis 层面天然分开，
 *    配合授权校验实现物理隔离。多个客户强烈建议再拆独立 Redis 实例（生产）。
 */
@Component
public class TagRedisRepository {

    private final StringRedisTemplate redisTemplate;

    public TagRedisRepository(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** 读生效版本指针。null = 该数据源还没生效过任何版本。 */
    public String getActiveVersion(String dataSourceId) {
        return redisTemplate.opsForValue().get("tag:active_version:" + dataSourceId);
    }

    /** 切换生效版本。SET 是原子操作 —— 这是版本切换"同一请求只命中同一版本"的基础。 */
    public void setActiveVersion(String dataSourceId, String versionKey) {
        redisTemplate.opsForValue().set("tag:active_version:" + dataSourceId, versionKey);
    }

    /**
     * 批量查询多个 ID 的标签。
     *
     * ⚡ 性能核心：用 Pipeline（管道）把 N 个 SMEMBERS 合并成 1 次网络往返。
     *    循环单查 1000 个 ID = 1000 次往返 × 0.5ms ≈ 500ms，P99 直接爆掉；
     *    Pipeline = 1 次往返 ≈ 几 ms。这是 P99 秒级达标的决定性手段。
     *
     * 📖 executePipelined 返回的 List 顺序与命令顺序一一对应，
     *    所以用 ids.get(i) 就能找回每个结果属于哪个 ID。
     */    public Map<String, Set<String>> batchGetTags(String dataSourceId, String versionKey,
                                                 List<String> ids) {
        List<Object> raw = redisTemplate.executePipelined((RedisConnection connection) -> {
            for (String id : ids) {
                connection.sMembers(buildKey(dataSourceId, versionKey, id).getBytes());
            }
            return null;   // 回调返回 null，结果由 executePipelined 收集
        });

        Map<String, Set<String>> result = new HashMap<>(ids.size() * 2);
        for (int i = 0; i < ids.size(); i++) {
            Object one = raw.get(i);
            Set<String> tags = new HashSet<>();
            if (one instanceof Collection) {
                for (Object t : (Collection<?>) one) {
                    tags.add(String.valueOf(t));
                }
            }
            // 未命中 = 空集合。调用方拿到空数组，不是错误（PDF 明确要求）。
            result.put(ids.get(i), tags);
        }
        return result;
    }

    /**
     * 批量写入一个 ID→标签 映射（入库用，Day 6），同样走 Pipeline。
     * ⚡ 每批几百~一千个 ID 刷一次管道，平衡内存与往返次数。
     */
    public void batchPutTags(String dataSourceId, String versionKey,
                             Map<String, List<String>> batch) {
        redisTemplate.executePipelined((RedisConnection connection) -> {
            batch.forEach((id, tags) -> {
                if (tags == null || tags.isEmpty()) {
                    return;   // 没标签的 ID 不占用 Redis 内存（⚡ 十亿级数据必须省）
                }
                byte[] key = buildKey(dataSourceId, versionKey, id).getBytes();
                byte[][] members = tags.stream().map(String::getBytes).toArray(byte[][]::new);
                connection.sAdd(key, members);
            });
            return null;
        });
    }

    /** 连通性检查（健康检查/降级用，Day 4） */
    public boolean ping() {
        try {
            return "PONG".equals(redisTemplate.getConnectionFactory()
                    .getConnection().ping());
        } catch (Exception e) {
            return false;
        }
    }

    /** 生成 Redis key：tag:{dataSourceId}:{versionKey}:{id} */
    private String buildKey(String dataSourceId, String versionKey, String id) {
        return "tag:" + dataSourceId + ":" + versionKey + ":" + id;
    }
}
