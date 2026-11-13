package com.wedding.job;

import com.wedding.common.config.RabbitConfig;
import com.wedding.common.context.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/** 公众号事件异步落库，回调主链路只做验签与快速应答。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MpEventConsumer {
    private final JdbcTemplate jdbc;

    @RabbitListener(queues = RabbitConfig.MP_EVENT)
    public void onEvent(Map<String, Object> message) {
        Long tenantId = message.get("tenantId") == null ? null : Long.valueOf(String.valueOf(message.get("tenantId")));
        if (tenantId == null) return;
        TenantContext.set(tenantId, null);
        try {
            log.info("公众号事件 tenant={} event={}", tenantId, message.get("event"));
        } finally {
            TenantContext.clear();
        }
    }
}
