package com.platform.tagquery.integration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * S3 文件操作封装（Day 5）。
 *
 * 📖 为什么自己包一层而不是业务代码直接用 AWS SDK：
 *    1. SDK 的创建/关闭/异常处理有固定套路，散落各处容易漏关连接；
 *    2. 以后换 MinIO/阿里云 OSS（都兼容 S3 协议）只改这一个类。
 *
 * 🔐 安全：AccessKey/SecretKey 从配置注入，生产环境走环境变量或密钥管理服务，
 *    绝不写死在代码/git 里（application.yml 里的值本地用，prod 用 --spring 参数覆盖）。
 */
@Component
public class S3FileClient {

    private final S3Client s3Client;

    public S3FileClient(@Value("${s3.endpoint}") String endpoint,
                        @Value("${s3.access-key}") String accessKey,
                        @Value("${s3.secret-key}") String secretKey,
                        @Value("${s3.region:us-east-1}") String region) {
        this.s3Client = S3Client.builder()
                .endpointOverride(URI.create(endpoint))   // 兼容 MinIO 等私有 S3
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .build();
    }

    /** 列出指定前缀下的所有文件 key（自动翻页） */
    public List<String> listObjectKeys(String bucket, String prefix) {
        List<String> keys = new ArrayList<>();
        String continuationToken = null;
        do {
            final String ct = continuationToken;   // lambda 要求变量 effectively final
            ListObjectsV2Response resp = s3Client.listObjectsV2(b -> b
                    .bucket(bucket).prefix(prefix)
                    .continuationToken(ct));
            resp.contents().forEach(obj -> keys.add(obj.key()));
            continuationToken = resp.nextContinuationToken();
        } while (continuationToken != null);
        return keys;
    }

    /**
     * 下载单个文件到本地目录。
     *
     * ⚡ 性能/稳定：流式拷贝（Files.copy(InputStream)），
     *    不把整个文件读进内存 —— 十亿级数据文件可能几个 G，readAllBytes 直接 OOM。
     */
    public Path downloadToLocal(String bucket, String key, Path localDir) throws IOException {
        Files.createDirectories(localDir);
        Path target = localDir.resolve(Paths.get(key).getFileName().toString());
        try (ResponseInputStream<GetObjectResponse> in =
                     s3Client.getObject(b -> b.bucket(bucket).key(key))) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return target;
    }

    /** 探活：前缀是否存在（拉取前预检，失败快速告警） */
    public boolean prefixExists(String bucket, String prefix) {
        ListObjectsV2Response resp = s3Client.listObjectsV2(b -> b
                .bucket(bucket).prefix(prefix).maxKeys(1));
        return !resp.contents().isEmpty();
    }
}
