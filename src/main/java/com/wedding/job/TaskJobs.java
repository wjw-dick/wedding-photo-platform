package com.wedding.job;

import com.wedding.biz.schedule.ScheduleService;
import com.wedding.common.config.RabbitConfig;
import com.wedding.common.context.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** 延时任务与周期任务：锁档释放、交付提醒、保留期清理、定时发布。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskJobs {
    private final ScheduleService scheduleService;
    private final JdbcTemplate jdbc;

    @RabbitListener(queues = RabbitConfig.DELAY + ".dlq")
    public void onDelay(Map<String, Object> message) {
        Long tenantId = message.get("tenantId") == null ? null : Long.valueOf(String.valueOf(message.get("tenantId")));
        if (tenantId == null) return;
        TenantContext.set(tenantId, null);
        try {
            if ("RELEASE_LOCK".equals(message.get("type"))) {
                String lockKey = String.valueOf(message.get("lockKey"));
                List<Map<String, Object>> rows = jdbc.queryForList(
                        "SELECT lock_key FROM sch_lock WHERE tenant_id=? AND lock_key=? AND status='LOCKED'",
                        tenantId, lockKey);
                if (!rows.isEmpty()) {
                    scheduleService.release(lockKey);
                    log.info("延时释放锁档 tenant={} lock={}", tenantId, lockKey);
                }
            }
        } catch (Exception e) {
            log.error("延时任务处理失败：{}", message, e);
        } finally {
            TenantContext.clear();
        }
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void releaseExpiredLocks() {
        try {
            int released = scheduleService.releaseExpired();
            if (released > 0) log.info("定时释放超时锁档 {} 条", released);
        } catch (Exception e) {
            log.error("释放超时锁档失败", e);
        }
    }

    @Scheduled(cron = "0 0 9 * * ?")
    public void dailyReminders() {
        for (Long tid : jdbc.queryForList("SELECT tenant_id FROM platform_tenant WHERE deleted=0", Long.class)) {
            TenantContext.set(tid, null);
            try {
                int days = 7;
                try {
                    Integer d = jdbc.queryForObject("SELECT CAST(param_value AS UNSIGNED) FROM sys_param WHERE tenant_id=? AND param_key='retain.remind.days'",
                            Integer.class, tid);
                    if (d != null) days = d;
                } catch (Exception ignored) {
                }
                jdbc.update("""
                        INSERT INTO sys_notice(tenant_id, title, content, channel)
                        SELECT ?, '客片保留期到期提醒',
                               CONCAT('订单 ', o.order_no, ' 的客片将于 ', d.retain_end, ' 到期，请提醒客户取件'),
                               'SITE'
                        FROM delivery_record d JOIN order_main o ON o.id=d.order_id AND o.tenant_id=d.tenant_id
                        WHERE d.tenant_id=? AND d.status<>'DONE'
                          AND d.retain_end = DATE_ADD(CURDATE(), INTERVAL ? DAY)
                        """, tid, tid, days);
                jdbc.update("""
                        UPDATE cms_article SET status='PUBLISHED'
                        WHERE tenant_id=? AND status='SCHEDULED' AND publish_at<=NOW()
                        """, tid);
                jdbc.update("""
                        UPDATE supplier_qualification SET remind_flag=1
                        WHERE tenant_id=? AND expire_date <= DATE_ADD(CURDATE(), INTERVAL 30 DAY) AND remind_flag=0
                        """, tid);
            } catch (Exception e) {
                log.error("租户 {} 定时提醒失败", tid, e);
            } finally {
                TenantContext.clear();
            }
        }
    }

    /** 保留期到期清理：仅清理已过期且未延期的交付记录，媒体记录保留审计。 */
    @Scheduled(cron = "0 30 2 * * ?")
    public void cleanupExpiredRetention() {
        for (Long tid : jdbc.queryForList("SELECT tenant_id FROM platform_tenant WHERE deleted=0", Long.class)) {
            TenantContext.set(tid, null);
            try {
                int n = jdbc.update("""
                        UPDATE delivery_record SET status='EXPIRED'
                        WHERE tenant_id=? AND status<>'DONE' AND extended<1 AND retain_end < CURDATE()
                        """, tid);
                if (n > 0) log.info("租户 {} 清理过期保留期 {} 条", tid, n);
            } catch (Exception e) {
                log.error("清理保留期失败 tid={}", tid, e);
            } finally {
                TenantContext.clear();
            }
        }
    }
}
