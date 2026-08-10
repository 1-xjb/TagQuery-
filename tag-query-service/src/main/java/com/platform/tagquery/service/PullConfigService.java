package com.platform.tagquery.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.platform.tagquery.exception.BizException;
import com.platform.tagquery.exception.ErrorCode;
import com.platform.tagquery.model.entity.PullConfig;
import com.platform.tagquery.repository.mysql.PullConfigMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 拉取配置服务（Day 5）—— 配置 CRUD。
 * 模式与 Day 2 的 AppKey 管理一致：XxxMapper + 增删改查。
 */
@Service
public class PullConfigService {

    private final PullConfigMapper pullConfigMapper;

    public PullConfigService(PullConfigMapper pullConfigMapper) {
        this.pullConfigMapper = pullConfigMapper;
    }

    public List<PullConfig> listAll() {
        return pullConfigMapper.selectList(null);
    }

    public PullConfig getById(Long id) {
        return pullConfigMapper.selectById(id);
    }

    public PullConfig create(PullConfig config) {
        if (config.getStatus() == null) {
            config.setStatus(1);
        }
        pullConfigMapper.insert(config);
        return config;
    }

    public PullConfig update(PullConfig config) {
        if (config.getId() == null) {
            throw new BizException(ErrorCode.PARAM_FORMAT_INVALID);
        }
        pullConfigMapper.updateById(config);
        return pullConfigMapper.selectById(config.getId());
    }

    public void toggleStatus(Long id, boolean enable) {
        PullConfig c = new PullConfig();
        c.setId(id);
        c.setStatus(enable ? 1 : 0);
        pullConfigMapper.updateById(c);
    }

    /** 所有启用中的配置（Day 8 调度器扫描用） */
    public List<PullConfig> getEnabledConfigs() {
        return pullConfigMapper.selectList(
                new LambdaQueryWrapper<PullConfig>().eq(PullConfig::getStatus, 1));
    }
}
