# 部署说明

## 前置

- Docker 24+ 与 Compose v2
- 域名：`wedding.example.com`（官网）、`admin.wedding.example.com`（后台）、`api.wedding.example.com`（API）
- TLS 证书放置 `deploy/nginx/certs/fullchain.pem` 与 `privkey.pem`（可用 certbot 签发）

## 步骤

```bash
cd deploy
cp .env.example .env
vi .env                      # 修改所有 change-me 密钥
docker compose up -d mysql redis rabbitmq minio
docker compose up -d app     # Flyway 自动建表
docker compose up -d nginx
```

健康检查：

```bash
curl -fsS http://127.0.0.1:8080/actuator/health
docker compose ps
```

## 前端构建

```bash
cd admin-web && npm ci && npm run build
cd ../pc-web && npm ci && npm run build
```

Nginx 直接挂载两个 `dist` 目录，无需额外容器。

## 初始化

1. 平台端登录 `admin / admin123`，立即修改密码。
2. 创建套餐、开通租户，记录一次性初始密码。
3. 租户端配置存储驱动（推荐 MinIO）、公众号凭证、短信网关。
4. 配置对象存储桶与 CDN 域名，验证上传/下载。
5. 导入门店、套系、员工、渠道与供应商基础数据。

## 升级

```bash
git pull
docker compose build app
docker compose up -d app
```

Flyway 自动执行新迁移；升级前执行 `backup.sh`。回滚使用上一镜像 tag + 数据库备份。

## 小程序与 App

- 4 个小程序工程位于 `miniprogram/{user,staff,supplier,channel}`，用微信开发者工具导入，修改 `project.config.json` 的 AppID 与 `utils/api.js` 的 BASE。
- Flutter App 位于 `app/`，按 `app/README.md` 生成平台目录后打包。
