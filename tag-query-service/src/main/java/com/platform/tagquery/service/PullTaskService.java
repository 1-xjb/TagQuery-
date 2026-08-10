package com.platform.tagquery.service;

import com.platform.tagquery.exception.BizException;
import com.platform.tagquery.exception.ErrorCode;
import com.platform.tagquery.integration.S3FileClient;
import com.platform.tagquery.metrics.QueryMetrics;
import com.platform.tagquery.model.entity.PullConfig;
import com.platform.tagquery.model.entity.PullTask;
import com.platform.tagquery.repository.mysql.PullConfigMapper;
import com.platform.tagquery.repository.mysql.PullTaskMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 拉取任务服务（Day 5/6/8）—— 手动触发 / 调度触发核心。
 *
 * 📖 状态机：PENDING → PULLING → PULLED / FAILED。
 *    每一步都落库 —— 任务中途崩了也能从表里看到停在哪一步（可恢复、可审计）。
 */
@Service
public class PullTaskService {

    @Value("${tag.pull.local-temp-dir:./data/pull_tmp}")
    private String localTempDir;

    private final PullConfigMapper pullConfigMapper;
    private final PullTaskMapper pullTaskMapper;
    private final S3FileClient s3FileClient;
    private final DataIntegrationService dataIntegrationService;
    private final NotificationService notificationService;
    private final QueryMetrics queryMetrics;

    public PullTaskService(PullConfigMapper pullConfigMapper, PullTaskMapper pullTaskMapper,
                           S3FileClient s3FileClient, DataIntegrationService dataIntegrationService,
                           NotificationService notificationService, QueryMetrics queryMetrics) {
        this.pullConfigMapper = pullConfigMapper;
        this.pullTaskMapper = pullTaskMapper;
        this.s3FileClient = s3FileClient;
        this.dataIntegrationService = dataIntegrationService;
        this.notificationService = notificationService;
        this.queryMetrics = queryMetrics;
    }

    public PullTask getById(Long id) {
        return pullTaskMapper.selectById(id);
    }

    public List<PullTask> listAll() {
        return pullTaskMapper.selectList(null);
    }

    /** 手动触发拉取 */
    public PullTask triggerManualPull(Long configId, String partition) {
        return doPull(configId, partition, "MANUAL");
    }

    /** 调度触发拉取（Day 8，cron 到点由 PullTaskScheduler 调用） */
    public PullTask triggerScheduledPull(Long configId) {
        return doPull(configId, null, "SCHEDULED");
    }

    /**
     * 拉取主流程：建任务 → 定分区 → 下载 → 记结果 →（成功则）自动启动集成。
     *
     * 📖 为什么整个方法不加 @Transactional：
     *    里面包含 S3 网络下载（可能几分钟），长事务会占着数据库连接不放。
     *    改为"每步单独更新状态"的短事务，牺牲一点一致性换可用性。
     */
    private PullTask doPull(Long configId, String partition, String triggerType) {
        PullConfig config = pullConfigMapper.selectById(configId);
        if (config == null || config.getStatus() == null || config.getStatus() != 1) {
            throw new BizException(ErrorCode.PARAM_FORMAT_INVALID);
        }

        // 1. 建任务（PENDING）
        PullTask task = new PullTask();
        task.setPullConfigId(configId);
        task.setTriggerType(triggerType);
        task.setStatus("PENDING");
        String targetPartition = (partition != null && !partition.isBlank())
                ? partition : resolvePartition(config);
        task.setPartitionName(targetPartition);
        task.setDataSourceId(config.getDataSourceId());   // Day 6：冗余数据源标识，免 join
        pullTaskMapper.insert(task);

        // 2. PULLING：预检 + 下载
        updateStatus(task.getId(), "PULLING", null);
        try {
            String prefix = config.getS3Prefix() + "/" + targetPartition + "/";
            if (!s3FileClient.prefixExists(config.getS3Bucket(), prefix)) {
                throw new IllegalStateException("S3 目标分区不存在: " + prefix);
            }
            List<String> keys = s3FileClient.listObjectKeys(config.getS3Bucket(), prefix);
            Path localDir = Paths.get(localTempDir, "pull_" + task.getId());
            long totalSize = 0;
            for (String key : keys) {
                Path f = s3FileClient.downloadToLocal(config.getS3Bucket(), key, localDir);
                totalSize += Files.size(f);
            }
            // 3. PULLED：记录成果
            task.setStatus("PULLED");
            task.setFileCount(keys.size());
            task.setFileTotalSize(totalSize);
            task.setFinishedAt(LocalDateTime.now());
            pullTaskMapper.updateById(task);
            queryMetrics.recordPull("PULLED");          // 拉取成功率埋点（Day 8）

            // 4. 📖 PDF 规则：拉取成功 → 自动启动数据集成（Day 6）
            dataIntegrationService.runIntegration(task.getId());
        } catch (Exception e) {
            // FAILED：记录原因 + 失败告警邮件（Day 8）
            updateStatus(task.getId(), "FAILED", e.getMessage());
            queryMetrics.recordPull("FAILED");          // 拉取成功率埋点（Day 8）
            Map<String, Object> vars = new HashMap<>();
            vars.put("customerName", config.getCustomerName());
            vars.put("dataSourceId", config.getDataSourceId());
            vars.put("triggerType", triggerType);
            vars.put("partition", targetPartition);
            vars.put("taskId", task.getId());
            vars.put("failureReason", e.getMessage());
            notificationService.sendPullFailureNotification(vars);
        }
        return task;
    }

    /**
     * 分区解析：LATEST=取前缀下"字典序最大"的分区目录（日期分区名天然有序）；
     *           CURRENT_MONTH=当月 yyyy-MM。
     * 📖 用字典序取巧：yyyy-MM-dd 格式的日期字符串，字典序 = 时间序，免解析。
     */
    private String resolvePartition(PullConfig config) {
        if ("CURRENT_MONTH".equals(config.getPartitionRule())) {
            return LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        }
        List<String> keys = s3FileClient.listObjectKeys(config.getS3Bucket(), config.getS3Prefix());
        return keys.stream()
                .map(k -> {
                    String rest = k.substring(config.getS3Prefix().length());
                    String[] seg = rest.split("/");
                    return seg.length >= 2 ? seg[1] : "";
                })
                .filter(s -> !s.isEmpty())
                .distinct().max(String::compareTo)
                .orElseThrow(() -> new IllegalStateException("S3 上没有任何分区"));
    }

    private void updateStatus(Long taskId, String status, String failureReason) {
        PullTask t = new PullTask();
        t.setId(taskId);
        t.setStatus(status);
        t.setFailureReason(failureReason);
        if ("PULLING".equals(status)) {
            t.setStartedAt(LocalDateTime.now());
        }
        pullTaskMapper.updateById(t);
    }
}
