package com.platform.tagquery.service;

import com.platform.tagquery.metrics.QueryMetrics;
import com.platform.tagquery.model.entity.IntegrationTask;
import com.platform.tagquery.model.entity.PullTask;
import com.platform.tagquery.repository.mysql.IntegrationTaskMapper;
import com.platform.tagquery.repository.mysql.PullTaskMapper;
import com.platform.tagquery.repository.redis.TagRedisRepository;
import com.platform.tagquery.util.DataParser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 数据集成服务（Day 6）：拉取下来的本地文件 → 流式解析 → 质量校验 → Pipeline 灌入新版本 Key 空间。
 *
 * ⚡ 性能第一原则：全程流式（BufferedReader 逐行），任何时刻内存里只有"一行 + 一批"。
 */
@Service
public class DataIntegrationService {

    private static final int BATCH_SIZE = 500;              // 每批刷一次管道的 ID 数
    private static final double MAX_DUP_RATE = 0.2;         // 重复率超阈值 → 失败
    private static final double MAX_NULL_RATE = 0.2;        // 空值率超阈值 → 失败

    @Value("${tag.pull.local-temp-dir:./data/pull_tmp}")
    private String localTempDir;

    private final PullTaskMapper pullTaskMapper;
    private final IntegrationTaskMapper integrationTaskMapper;
    private final TagRedisRepository tagRedis;
    private final VersionService versionService;
    private final NotificationService notificationService;
    private final QueryMetrics queryMetrics;

    public DataIntegrationService(PullTaskMapper pullTaskMapper,
                                  IntegrationTaskMapper integrationTaskMapper,
                                  TagRedisRepository tagRedis,
                                  VersionService versionService,
                                  NotificationService notificationService,
                                  QueryMetrics queryMetrics) {
        this.pullTaskMapper = pullTaskMapper;
        this.integrationTaskMapper = integrationTaskMapper;
        this.tagRedis = tagRedis;
        this.versionService = versionService;
        this.notificationService = notificationService;
        this.queryMetrics = queryMetrics;
    }

    /**
     * 集成主流程：PARSING → VALIDATING → LOADING → SUCCESS / FAILED。
     * 📖 从 PullTask 的本地暂存目录读全部文件（可能多个），逐行解析，统计质量指标。
     */
    public IntegrationTask runIntegration(Long pullTaskId) {
        PullTask pullTask = pullTaskMapper.selectById(pullTaskId);
        if (pullTask == null || pullTask.getDataSourceId() == null) {
            throw new IllegalArgumentException("pull_task 不存在或缺少 dataSourceId: " + pullTaskId);
        }

        IntegrationTask task = new IntegrationTask();
        task.setPullTaskId(pullTaskId);
        task.setDataSourceId(pullTask.getDataSourceId());
        task.setStatus("PARSING");
        task.setStartedAt(LocalDateTime.now());
        integrationTaskMapper.insert(task);

        // 新版本号：数据源 + 日期 + 任务 id，可读且唯一
        String versionKey = "v_" + task.getDataSourceId() + "_"
                + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                + "_" + pullTaskId;

        long total = 0, valid = 0, nullId = 0, invalidFmt = 0, duplicate = 0;
        Set<String> seenIds = new HashSet<>();      // ⚠️ 十亿级要换布隆过滤器（开发期 Set 够用）
        Map<String, List<String>> batch = new HashMap<>(BATCH_SIZE * 2);

        try {
            Path dir = Paths.get(localTempDir, "pull_" + pullTaskId);
            List<Path> files;
            try (Stream<Path> stream = Files.list(dir)) {
                files = stream.filter(Files::isRegularFile).collect(ArrayList::new, List::add, List::addAll);
            }
            if (files.isEmpty()) {
                throw new IllegalStateException("本地暂存目录无文件可解析: " + dir);
            }

            for (Path file : files) {
                try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    String line;
                    int lineNo = 0;
                    while ((line = reader.readLine()) != null) {
                        lineNo++;
                        total++;
                        DataParser.ParsedRecord rec = DataParser.parseLine(line, lineNo);
                        if (rec == null) { invalidFmt++; continue; }
                        if (rec.userId.isEmpty()) { nullId++; continue; }
                        if (!seenIds.add(rec.userId)) { duplicate++; continue; }  // 重复只留第一条
                        valid++;
                        batch.put(rec.userId, rec.tagCodes);
                        if (batch.size() >= BATCH_SIZE) {
                            tagRedis.batchPutTags(task.getDataSourceId(), versionKey, batch);
                            batch.clear();
                        }
                    }
                }
            }
            if (!batch.isEmpty()) {
                tagRedis.batchPutTags(task.getDataSourceId(), versionKey, batch);
            }

            // ---- 质量校验（PDF：重复率/空值率超阈值 → 失败，不生成可生效版本）----
            double dupRate = total == 0 ? 0 : (double) duplicate / total;
            double nullRate = total == 0 ? 0 : (double) nullId / total;
            task.setTotalRecords(total);
            task.setValidRecords(valid);
            task.setDuplicateRate(BigDecimal.valueOf(dupRate));
            task.setNullRate(BigDecimal.valueOf(nullRate));
            if (dupRate > MAX_DUP_RATE || nullRate > MAX_NULL_RATE) {
                task.setStatus("FAILED");
                task.setFailureReason("重复率/空值率超阈值: dup=" + dupRate + ", null=" + nullRate);
                integrationTaskMapper.updateById(task);
                queryMetrics.recordIntegration("FAILED");        // 集成成功率埋点
                // Day 8：失败告警邮件
                notificationService.sendIntegrationFailureNotification(buildVars(task));
                return task;      // 📖 数据已灌进新 key 空间但不生效、不建版本，异步清理即可
            }

            task.setStatus("SUCCESS");
            task.setFinishedAt(LocalDateTime.now());
            integrationTaskMapper.updateById(task);
            queryMetrics.recordIntegration("SUCCESS");          // 集成成功率埋点

            // 📖 成功 → 创建版本（Day 7），校验通过后生效
            versionService.createVersion(task.getDataSourceId(), task.getId(), valid, versionKey);

            // Day 8：同步完成通知邮件
            Map<String, Object> vars = new HashMap<>();
            vars.put("dataSourceId", task.getDataSourceId());
            vars.put("versionId", versionKey);
            vars.put("versionStatus", "PASSED");
            vars.put("idCount", valid);
            vars.put("fileCount", pullTask == null ? 0 : pullTask.getFileCount());
            vars.put("fileSize", pullTask == null ? 0 : pullTask.getFileTotalSize());
            notificationService.sendTaskCompleteNotification(vars);
        } catch (Exception e) {
            task.setStatus("FAILED");
            task.setFailureReason(e.getMessage());
            integrationTaskMapper.updateById(task);
            queryMetrics.recordIntegration("FAILED");        // 集成成功率埋点
            // Day 8：失败告警邮件
            notificationService.sendIntegrationFailureNotification(buildVars(task));
        }
        return task;
    }

    /** 集成失败通知变量 */
    private Map<String, Object> buildVars(IntegrationTask task) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("dataSourceId", task.getDataSourceId());
        vars.put("pullTaskId", task.getPullTaskId());
        vars.put("failureReason", task.getFailureReason());
        vars.put("totalRecords", task.getTotalRecords());
        vars.put("validRecords", task.getValidRecords());
        return vars;
    }
}
