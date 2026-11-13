# 技术栈配置表（唯一事实源）

> 后续版本、连接参数、备份策略变更必须先改本表，再同步 `PRO.md`、技术方案与部署文件。

## 1. 版本与用途

| 层级 | 选型 | 版本 | 关键配置 |
|---|---|---|---|
| 后端语言 | Java | 17 LTS | `-Xms512m -Xmx1536m -XX:+UseG1GC`，时区 `Asia/Shanghai` |
| 后端框架 | Spring Boot + MyBatis-Plus | 3.2.5 + 3.5.5 | 统一异常、租户拦截、审计切面、逻辑删除、分页 |
| 数据库 | MySQL | 8.0.x | utf8mb4 / utf8mb4_0900_ai_ci；Hikari max 50、min 10、connectionTimeout 30s、idleTimeout 10min |
| 缓存 | Redis | 7.x | 会话、档期锁 `tenant:{id}:slot:{date}:{resourceId}`、限流、参数缓存；maxmemory 2g，allkeys-lru |
| 消息队列 | RabbitMQ | 3.13.x | vhost `/wedding`；队列 `msg.notify`、`report.async`、`mp.event`、`task.delay.dlq`；重试 3 次 |
| 后台前端 | Vue 3 + Vite + Element Plus | 3.4.x / 4.5.x / 2.7.x | 8px 栅格、圆角 6px、统一布局 |
| 官网前端 | Vue 3 + Vite | 3.4.x / 4.5.x | SSR 不启用，静态构建 + SEO 元数据 + sitemap/robots |
| 小程序 | 微信原生 | 基础库 3.x | 4 个独立工程，共用请求层 |
| App | Flutter | 3.24.x（源码兼容 3.19+） | `http` + `shared_preferences`；生产可替换 Riverpod/Dio/Hive |
| 对象存储 | MinIO 默认，5 驱动可切 | RELEASE.2024.x | Endpoint/Bucket/Region/AK/SK；私有桶 + 签名 URL ≤30min |
| 打印/PDF | OpenPDF | 1.3.39 | 中文字体 `STSong-Light` |
| 二维码 | ZXing | 3.5.3 | PNG 输出，尺寸 120–1024 |
| 反向代理 | Nginx | 1.24 | HTTPS、子域名、限流、静态缓存 |
| 部署 | Docker Compose | 24.x | mysql/redis/rabbitmq/minio/app/nginx/prometheus |
| 监控 | Prometheus + Grafana | 2.52 | `/actuator/health`、5xx、P95、连接池、队列积压、磁盘 |

## 2. 连接参数（环境变量）

| 变量 | 默认（开发） | 生产 | 说明 |
|---|---|---|---|
| `SERVER_PORT` | 8080 | 8080 | 应用端口 |
| `MYSQL_HOST` / `MYSQL_PORT` | 127.0.0.1 / 3307 | mysql / 3306 | 数据库 |
| `MYSQL_DB` | wedding_platform | wedding_platform | 库名 |
| `MYSQL_USER` / `MYSQL_PASSWORD` | root / wedding123 | 由 `.env` 注入 | 禁止默认口令上线 |
| `REDIS_HOST` / `REDIS_PORT` | 127.0.0.1 / 6380 | redis / 6379 | 缓存 |
| `REDIS_PASSWORD` | 空 | 强口令 | 生产必须设置 |
| `RABBIT_HOST` / `RABBIT_PORT` | 127.0.0.1 / 5673 | rabbitmq / 5672 | 队列 |
| `RABBIT_VHOST` | /wedding | /wedding | 虚拟主机 |
| `JWT_SECRET` | 开发默认 | ≥48 字符随机 | 变更会使旧 Token 失效 |
| `AES_KEY` | 开发默认 | 32 字节随机 | 加密 AppSecret/AK/SK/SSO Secret |
| `STORAGE_LOCAL_ROOT` | ./data/storage | /app/data/storage | 本地存储根目录 |
| `MP_CALLBACK_BASE` | http://localhost:8080 | https://api.example.com | 公众号服务器 URL 前缀 |

## 3. 备份与保留

| 项 | 策略 |
|---|---|
| MySQL | 每日 02:00 全量，保留 30 份；每周一归档 12 周；SHA-256 校验 |
| Redis | AOF 持久化；不作为唯一数据源 |
| 对象存储 | 版本保护 + 回收站 30 天；每月文件数/容量对账 |
| 应用日志 | 100MB 滚动，保留 30 天 |
| 审计日志 | ≥180 天，只读 |
| 恢复演练 | 每季度一次，记录 RTO/RPO |

## 4. 端口与网络

| 服务 | 宿主端口 | 容器端口 | 对外 |
|---|---|---|---|
| Nginx HTTP/HTTPS | 80/443 | 80/443 | 是 |
| 应用 | 127.0.0.1:8080 | 8080 | 仅经 Nginx |
| MySQL | 127.0.0.1:3306 | 3306 | 否 |
| Redis | 127.0.0.1:6379 | 6379 | 否 |
| RabbitMQ | 127.0.0.1:5672/15672 | 5672/15672 | 否 |
| MinIO | 127.0.0.1:9000/9001 | 9000/9001 | 否 |
| Prometheus | 127.0.0.1:9090 | 9090 | 否 |

## 5. 变更记录

| 日期 | 版本 | 变更 | 影响 |
|---|---|---|---|
| 2026-11 | v1.0 | 初版，与 PRO 第四章一致 | 基线 |
