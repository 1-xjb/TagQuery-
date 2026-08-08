package com.platform.tagquery.controller;


import com.platform.tagquery.model.entity.AppKey;
import com.platform.tagquery.repository.mysql.AppKeyMapper;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/v1/admin/app-keys")
public class AppKeyController {
    private final AppKeyMapper appKeyMapper;

    public AppKeyController(AppKeyMapper appKeyMapper) {
        this.appKeyMapper = appKeyMapper;
    }

    /**
     * 注册新 AppKey。
     * 🔐 密钥用 SecureRandom 生成（密码学安全随机数），
     *    禁止用 Math.random()/UUID 当密钥 —— 前者可预测，后者熵不够。
     */

    @PostMapping
    public Map<String , Object> create(@RequestParam String appKey , @RequestParam String appName){
        AppKey entity = new AppKey();
        entity.setAppKey(appKey);
        entity.setAppName(appName);
        entity.setAppSecret(generateSecret());
        entity.setStatus(1);
        entity.setQpsLimit(100);
        appKeyMapper.insert(entity);

        Map<String , Object> result = new LinkedHashMap<>();
        result.put("code" , 0);
        result.put("message" , "success");
        result.put("appSecret" , entity.getAppSecret());
        // 🔐 密钥只在创建时返回这一次，之后平台不保存明文可查入口（生产应加密存储）
        return result;
    }

    @PatchMapping("/{id}/status")
    public Map<String , Object> toggle(@PathVariable Long id , @RequestParam boolean enable){
        AppKey entity = new AppKey();
        entity.setId(id);
        entity.setStatus(enable ? 1:0);
        appKeyMapper.updateById(entity);

        Map<String , Object> result = new LinkedHashMap<>();
        result.put("code", 0);
        result.put("message","success");
        return result;
    }

    private String generateSecret(){
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        StringBuilder sb = new StringBuilder(64);
        for(byte b : bytes){
            sb.append(String.format("%02x" , b));
        }
        return sb.toString();
    }
}
