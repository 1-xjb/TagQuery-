# 实时标签查询服务

统一实时标签查询后端服务，基于 Spring Boot 2.7 + MyBatis-Plus + Redis 构建。

## 技术栈

- Spring Boot 2.7.18
- MyBatis-Plus 3.5.7
- MySQL + Flyway
- Redis
- XXL-Job 分布式调度
- AWS S3
- Prometheus + Actuator 监控

## 快速开始

```bash
# 编译
mvn clean package

# 启动
java -jar target/tag-query-service.jar
```
