package com.wedding.job;

import com.wedding.common.config.RabbitConfig;
import com.wedding.common.context.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/** 消息通知消费者：消息体必须带 tenantId，失败重试 3 次后进入死信。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotifyConsumer {
    private final JdbcTemplate jdbc;

    @RabbitListener(queues = RabbitConfig.NOTIFY)
    public void onNotify(Map<String, Object> message) {
        Long tenantId = message.get("tenantId") == null ? null : Long.valueOf(String.valueOf(message.get("tenantId")));
        if (tenantId == null) {
            log.warn("通知消息缺少 tenantId：{}", message);
            return;
        }
        TenantContext.set(tenantId, null);
        try {
            String node = String.valueOf(message.getOrDefault("node", "NOTIFY"));
            String title = switch (node) {
                case "ORDER_STATUS" -> "订单状态变更";
                case "ORDER_PAID" -> "订单收款成功";
                case "SHOOT_DISPATCH" -> "拍摄任务派工";
                default -> "业务通知";
            };
            jdbc.update("""
                    INSERT INTO sys_notice(tenant_id, title, content, channel) VALUES (?,?,?, 'SITE')
                    """, tenantId, title, message.toString());
        } finally {
            TenantContext.clear();
        }
    }
}
