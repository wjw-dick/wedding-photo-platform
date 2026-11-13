# 备份与恢复

## 策略

| 项 | 方案 |
|---|---|
| 数据库 | 每日 02:00 全量 `mysqldump --single-transaction`，保留 30 天 |
| 周归档 | 每周一额外保留一份，保留 12 周 |
| 对象存储 | MinIO 版本保护 + 回收站 30 天；每日镜像到备份卷 |
| 校验 | 每次备份生成 SHA-256，恢复前强校验 |
| 演练 | 每季度在隔离环境执行一次恢复并记录耗时 |

## 安装定时任务

```bash
chmod +x deploy/backup/*.sh
crontab -e
# 每天 02:00
0 2 * * * /opt/wedding/deploy/backup/backup.sh >> /var/log/wedding-backup.log 2>&1
```

## 手动恢复

```bash
./deploy/backup/restore.sh /backup/20261113-020000
```

恢复脚本会先做一次安全备份、停止应用、重建数据库、导入、再启动并做健康检查。生产恢复必须双人复核。
