# 监控与告警

## 组件

- Prometheus 抓取应用 `/actuator/prometheus`（需引入 micrometer-registry-prometheus，或使用 Actuator 基础指标）。
- 建议增加 `mysqld-exporter`、`redis_exporter`、RabbitMQ `rabbitmq_prometheus` 插件。
- Grafana 面板：QPS、P95、5xx、JVM 堆、Hikari 连接池、队列积压、磁盘。

## 告警通道

在 Alertmanager 配置企业微信/钉钉 Webhook：

```yaml
receivers:
  - name: ops
    webhook_configs:
      - url: https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=REPLACE
```

分级：critical 立即电话 + 群消息；warning 群消息，30 分钟未恢复升级。

## 日志

- 应用日志：`/app/logs/wedding-platform.log`，100MB 滚动，保留 30 天。
- Nginx：access/error 日志，ELK 或 Loki 采集。
- 审计日志：库表 `sys_audit` / `platform_audit`，只读，保留 180 天。
