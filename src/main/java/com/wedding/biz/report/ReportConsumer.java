package com.wedding.biz.report;

import com.wedding.common.config.RabbitConfig;
import com.wedding.common.context.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/** 报表异步队列消费者：消息必须携带 tenantId。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReportConsumer {
    private final ReportService reportService;

    @RabbitListener(queues = RabbitConfig.REPORT)
    public void onReport(Map<String, Object> message) {
        Long tenantId = message.get("tenantId") == null ? null : Long.valueOf(String.valueOf(message.get("tenantId")));
        Long taskId = message.get("taskId") == null ? null : Long.valueOf(String.valueOf(message.get("taskId")));
        if (tenantId == null || taskId == null) {
            log.warn("报表消息缺少 tenantId/taskId：{}", message);
            return;
        }
        TenantContext.set(tenantId, null);
        try {
            reportService.generate(taskId);
        } finally {
            TenantContext.clear();
        }
    }
}
