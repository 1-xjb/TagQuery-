package com.platform.tagquery.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.platform.tagquery.exception.BizException;
import com.platform.tagquery.exception.ErrorCode;
import com.platform.tagquery.model.entity.DataVersion;
import com.platform.tagquery.model.entity.VersionEventLog;
import com.platform.tagquery.repository.mysql.DataVersionMapper;
import com.platform.tagquery.repository.mysql.VersionEventLogMapper;
import com.platform.tagquery.repository.redis.TagRedisRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 版本服务（Day 7）：生成 / 校验 / 生效 / 回退 四动作闭环。
 */
@Service
public class VersionService {

    private final DataVersionMapper dataVersionMapper;
    private final VersionEventLogMapper versionEventLogMapper;
    private final TagRedisRepository tagRedis;

    public VersionService(DataVersionMapper dataVersionMapper,
                          VersionEventLogMapper versionEventLogMapper,
                          TagRedisRepository tagRedis) {
        this.dataVersionMapper = dataVersionMapper;
        this.versionEventLogMapper = versionEventLogMapper;
        this.tagRedis = tagRedis;
    }

    /**
     * 集成成功后创建版本（Day 6 调用）。
     * 📖 集成阶段已做过数据质量校验，所以这里直接标记 PASSED（可生效），activeStatus=INACTIVE。
     */
    public DataVersion createVersion(String dataSourceId, Long integrationTaskId,
                                     Long recordCount, String versionKey) {
        DataVersion v = new DataVersion();
        v.setVersionKey(versionKey);
        v.setDataSourceId(dataSourceId);
        v.setIntegrationTaskId(integrationTaskId);
        v.setRecordCount(recordCount);
        v.setValidateStatus("PASSED");
        v.setActiveStatus("INACTIVE");
        dataVersionMapper.insert(v);
        return v;
    }

    public List<DataVersion> listVersions(String dataSourceId) {
        return dataVersionMapper.selectList(
                new LambdaQueryWrapper<DataVersion>()
                        .eq(DataVersion::getDataSourceId, dataSourceId)
                        .orderByDesc(DataVersion::getId));
    }

    public DataVersion getActiveVersion(String dataSourceId) {
        return dataVersionMapper.selectOne(
                new LambdaQueryWrapper<DataVersion>()
                        .eq(DataVersion::getDataSourceId, dataSourceId)
                        .eq(DataVersion::getActiveStatus, "ACTIVE"));
    }

    /**
     * 生效版本 —— 全项目对"正确性"要求最高的一小段代码。
     *
     * 📖 顺序有讲究（为什么先切 Redis 指针再更新 DB）：
     *    查询链路只读 Redis 指针。先切指针，查询立刻用新数据；
     *    再更新 DB 状态（展示用）。即使 DB 更新失败，查询行为依然正确。
     *
     * 🔐 并发防护：管理端操作低频，单机 synchronized 够用；多实例部署改 Redis 分布式锁。
     */
    @Transactional
    public synchronized void activateVersion(Long versionId, String reason) {
        doActivate(versionId, reason, "ACTIVATE");
    }

    /**
     * 回退：找"上一可用版本"切回去。
     * 📖 什么是"上一可用"：同数据源、校验 PASSED、非当前生效、按激活时间倒序第一个。
     */
    @Transactional
    public synchronized DataVersion rollbackVersion(String dataSourceId, String reason) {
        DataVersion current = getActiveVersion(dataSourceId);
        DataVersion previous = dataVersionMapper.selectOne(
                new LambdaQueryWrapper<DataVersion>()
                        .eq(DataVersion::getDataSourceId, dataSourceId)
                        .eq(DataVersion::getValidateStatus, "PASSED")
                        .ne(current != null, DataVersion::getId, current == null ? -1 : current.getId())
                        .orderByDesc(DataVersion::getActivatedAt)
                        .last("LIMIT 1"));
        if (previous == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR);  // 没有可回退版本（保持现状）
        }
        doActivate(previous.getId(), "ROLLBACK: " + reason, "ROLLBACK");  // 复用生效逻辑，动作记 ROLLBACK
        return previous;
    }

    /** 生效核心逻辑（被 activate/rollback 复用，DB 事务由外层公开方法保证） */
    private void doActivate(Long versionId, String reason, String action) {
        DataVersion version = dataVersionMapper.selectById(versionId);
        // 规则：只有校验通过的版本才允许生效（PDF 硬性要求）
        if (version == null || !"PASSED".equals(version.getValidateStatus())) {
            throw new BizException(ErrorCode.PARAM_FORMAT_INVALID);
        }

        String dsId = version.getDataSourceId();
        // 1. 找当前生效版本（用于日志 from）
        DataVersion currentActive = getActiveVersion(dsId);

        // 2. 切 Redis 指针（原子，查询无感知）
        tagRedis.setActiveVersion(dsId, version.getVersionKey());

        // 3. 更 DB：旧的 ACTIVE→DEPRECATED，新的→ACTIVE
        if (currentActive != null) {
            currentActive.setActiveStatus("DEPRECATED");
            dataVersionMapper.updateById(currentActive);
        }
        version.setActiveStatus("ACTIVE");
        version.setActivatedAt(LocalDateTime.now());
        dataVersionMapper.updateById(version);

        // 4. 记事件日志（PDF：生效动作需记录旧版本、新版本、时间、结果）
        VersionEventLog event = new VersionEventLog();
        event.setDataSourceId(dsId);
        event.setAction(action);
        event.setFromVersionKey(currentActive == null ? null : currentActive.getVersionKey());
        event.setToVersionKey(version.getVersionKey());
        event.setResult("SUCCESS");
        event.setReason(reason);
        versionEventLogMapper.insert(event);
    }
}
