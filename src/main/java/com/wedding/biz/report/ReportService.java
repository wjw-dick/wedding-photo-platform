package com.wedding.biz.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wedding.common.api.BizException;
import com.wedding.common.api.PageResult;
import com.wedding.common.context.LoginContext;
import com.wedding.common.context.TenantContext;
import com.wedding.common.storage.StorageManager;
import com.wedding.common.util.BizNo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;

/** 9 类统计指标与异步 Excel（CSV，Excel 可直接打开）导出。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {
    private final JdbcTemplate jdbc;
    private final StorageManager storageManager;
    private final BizNo bizNo;
    private final ObjectMapper objectMapper;
    private final RabbitTemplate rabbitTemplate;

    public Map<String, Object> overview(String from, String to) {
        Long tid = TenantContext.requireTenantId();
        if (from == null || from.isBlank()) from = LocalDate.now().withDayOfMonth(1).toString();
        if (to == null || to.isBlank()) to = LocalDate.now().toString();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("period", Map.of("from", from, "to", to));

        result.put("orders", jdbc.queryForMap("""
                SELECT COUNT(*) AS orderCount,
                       COALESCE(SUM(total_amount),0) AS totalAmount,
                       COALESCE(SUM(paid_amount),0) AS paidAmount,
                       COALESCE(SUM(deposit_amount),0) AS depositAmount,
                       COALESCE(SUM(balance_amount),0) AS balanceAmount,
                       COALESCE(SUM(addon_amount),0) AS addonAmount,
                       COALESCE(SUM(refund_amount),0) AS refundAmount
                FROM order_main WHERE tenant_id=? AND deleted=0 AND DATE(created_at) BETWEEN ? AND ?
                """, tid, from, to));

        result.put("revenueByDay", jdbc.queryForList("""
                SELECT DATE(created_at) AS day, COUNT(*) AS orders, COALESCE(SUM(paid_amount),0) AS paid
                FROM order_main WHERE tenant_id=? AND deleted=0 AND DATE(created_at) BETWEEN ? AND ?
                GROUP BY DATE(created_at) ORDER BY day
                """, tid, from, to));

        result.put("schedule", jdbc.queryForMap("""
                SELECT COUNT(*) AS slotCount, COALESCE(SUM(capacity),0) AS capacity, COALESCE(SUM(used_count),0) AS used,
                       ROUND(COALESCE(SUM(used_count),0) / GREATEST(COALESCE(SUM(capacity),0),1) * 100, 2) AS utilizationRate
                FROM sch_slot WHERE tenant_id=? AND slot_date BETWEEN ? AND ?
                """, tid, from, to));

        result.put("capacity", jdbc.queryForMap("""
                SELECT
                  (SELECT COUNT(*) FROM shoot_task WHERE tenant_id=? AND shoot_date BETWEEN ? AND ?) AS shootTasks,
                  (SELECT COUNT(*) FROM retouch_task WHERE tenant_id=? AND DATE(created_at) BETWEEN ? AND ?) AS retouchTasks,
                  (SELECT COUNT(*) FROM media_file WHERE tenant_id=? AND DATE(created_at) BETWEEN ? AND ?) AS mediaFiles
                """, tid, from, to, tid, from, to, tid, from, to));

        result.put("delivery", jdbc.queryForMap("""
                SELECT COUNT(*) AS delivered,
                       ROUND(AVG(DATEDIFF(delivered_at, created_at)), 1) AS avgDays
                FROM delivery_record WHERE tenant_id=? AND delivered_at IS NOT NULL
                  AND DATE(delivered_at) BETWEEN ? AND ?
                """, tid, from, to));

        result.put("channelContribution", jdbc.queryForList("""
                SELECT c.id, c.channel_name, COUNT(o.id) AS orders, COALESCE(SUM(o.total_amount),0) AS amount,
                       COALESCE(SUM(cr.amount),0) AS commission
                FROM channel c
                LEFT JOIN order_main o ON o.channel_id=c.id AND o.tenant_id=c.tenant_id AND o.deleted=0
                     AND DATE(o.created_at) BETWEEN ? AND ?
                LEFT JOIN commission_record cr ON cr.order_id=o.id AND cr.tenant_id=o.tenant_id
                WHERE c.tenant_id=? AND c.deleted=0
                GROUP BY c.id, c.channel_name ORDER BY amount DESC
                """, from, to, tid));

        result.put("storePerformance", jdbc.queryForList("""
                SELECT s.id, s.store_name, COUNT(o.id) AS orders, COALESCE(SUM(o.total_amount),0) AS amount
                FROM sys_store s
                LEFT JOIN order_main o ON o.store_id=s.id AND o.tenant_id=s.tenant_id AND o.deleted=0
                     AND DATE(o.created_at) BETWEEN ? AND ?
                WHERE s.tenant_id=? AND s.deleted=0
                GROUP BY s.id, s.store_name ORDER BY amount DESC
                """, from, to, tid));

        result.put("employeePerformance", jdbc.queryForList("""
                SELECT e.id, e.real_name, e.role_type, COUNT(cr.id) AS items, COALESCE(SUM(cr.amount),0) AS commission
                FROM employee e
                LEFT JOIN commission_record cr ON cr.employee_id=e.id AND cr.tenant_id=e.tenant_id
                     AND DATE(cr.created_at) BETWEEN ? AND ?
                WHERE e.tenant_id=? AND e.deleted=0
                GROUP BY e.id, e.real_name, e.role_type ORDER BY commission DESC
                """, from, to, tid));

        BigDecimal total = (BigDecimal) ((Map<?, ?>) result.get("orders")).get("totalAmount");
        BigDecimal refund = (BigDecimal) ((Map<?, ?>) result.get("orders")).get("refundAmount");
        result.put("refundRate", total.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO
                : refund.multiply(BigDecimal.valueOf(100)).divide(total, 2, RoundingMode.HALF_UP));
        return result;
    }

    @Transactional
    public Map<String, Object> createTask(String reportType, Map<String, Object> params) {
        Long tid = TenantContext.requireTenantId();
        String no = bizNo.next("RP");
        String paramsJson;
        try {
            paramsJson = objectMapper.writeValueAsString(params == null ? Map.of() : params);
        } catch (Exception e) {
            paramsJson = "{}";
        }
        jdbc.update("""
                INSERT INTO report_task(tenant_id, task_no, report_type, params_json, status, created_by)
                VALUES (?,?,?,?, 'PENDING', ?)
                """, tid, no, reportType, paramsJson, LoginContext.require().getId());
        Long taskId = jdbc.queryForObject("SELECT id FROM report_task WHERE tenant_id=? AND task_no=?", Long.class, tid, no);
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("tenantId", tid);
        msg.put("taskId", taskId);
        msg.put("reportType", reportType);
        msg.put("params", params);
        try {
            rabbitTemplate.convertAndSend(com.wedding.common.config.RabbitConfig.REPORT, msg);
        } catch (Exception e) {
            log.warn("报表消息投递失败，改为同步生成", e);
            generate(taskId);
        }
        return Map.of("taskId", taskId, "taskNo", no, "status", "PENDING");
    }

    /** 消费端与同步兜底共用：生成 CSV 并写对象存储。 */
    public void generate(Long taskId) {
        Long tid = TenantContext.requireTenantId();
        Map<String, Object> task = jdbc.queryForMap("SELECT * FROM report_task WHERE tenant_id=? AND id=?", tid, taskId);
        try {
            jdbc.update("UPDATE report_task SET status='RUNNING' WHERE tenant_id=? AND id=?", tid, taskId);
            @SuppressWarnings("unchecked")
            Map<String, Object> params = objectMapper.readValue(String.valueOf(task.get("params_json")), Map.class);
            String from = params.get("from") == null ? LocalDate.now().withDayOfMonth(1).toString() : String.valueOf(params.get("from"));
            String to = params.get("to") == null ? LocalDate.now().toString() : String.valueOf(params.get("to"));
            String csv = buildCsv(String.valueOf(task.get("report_type")), from, to);
            byte[] bytes = ("\uFEFF" + csv).getBytes(StandardCharsets.UTF_8);
            StorageManager.UploadResult up = storageManager.upload(tid, "REPORT", null,
                    task.get("task_no") + ".csv", new ByteArrayInputStream(bytes), bytes.length, "text/csv");
            jdbc.update("UPDATE report_task SET status='DONE', file_url=? WHERE tenant_id=? AND id=?",
                    up.objectKey(), tid, taskId);
        } catch (Exception e) {
            log.error("报表生成失败 task={}", taskId, e);
            jdbc.update("UPDATE report_task SET status='FAILED', error_msg=? WHERE tenant_id=? AND id=?",
                    e.getMessage(), tid, taskId);
        }
    }

    private String buildCsv(String type, String from, String to) {
        Long tid = TenantContext.requireTenantId();
        StringBuilder sb = new StringBuilder();
        switch (type) {
            case "orders" -> {
                sb.append("订单号,客户,手机号,状态,总金额,已付,退款,拍摄日期,创建时间\n");
                for (Map<String, Object> r : jdbc.queryForList("""
                        SELECT * FROM order_main WHERE tenant_id=? AND deleted=0 AND DATE(created_at) BETWEEN ? AND ? ORDER BY id
                        """, tid, from, to)) {
                    sb.append(csv(r.get("order_no"))).append(',').append(csv(r.get("customer_name"))).append(',')
                            .append(csv(r.get("customer_phone"))).append(',').append(csv(r.get("status"))).append(',')
                            .append(r.get("total_amount")).append(',').append(r.get("paid_amount")).append(',')
                            .append(r.get("refund_amount")).append(',').append(csv(r.get("shoot_date"))).append(',')
                            .append(csv(r.get("created_at"))).append('\n');
                }
            }
            case "revenue" -> {
                sb.append("日期,订单数,收款金额\n");
                for (Map<String, Object> r : jdbc.queryForList("""
                        SELECT DATE(created_at) AS day, COUNT(*) AS orders, COALESCE(SUM(paid_amount),0) AS paid
                        FROM order_main WHERE tenant_id=? AND deleted=0 AND DATE(created_at) BETWEEN ? AND ?
                        GROUP BY DATE(created_at) ORDER BY day
                        """, tid, from, to)) {
                    sb.append(r.get("day")).append(',').append(r.get("orders")).append(',').append(r.get("paid")).append('\n');
                }
            }
            case "channel" -> {
                sb.append("渠道,订单数,成交金额,佣金\n");
                for (Map<String, Object> r : jdbc.queryForList("""
                        SELECT c.channel_name, COUNT(o.id) AS orders, COALESCE(SUM(o.total_amount),0) AS amount,
                               COALESCE(SUM(cr.amount),0) AS commission
                        FROM channel c LEFT JOIN order_main o ON o.channel_id=c.id AND o.tenant_id=c.tenant_id
                             AND DATE(o.created_at) BETWEEN ? AND ?
                        LEFT JOIN commission_record cr ON cr.order_id=o.id AND cr.tenant_id=o.tenant_id
                        WHERE c.tenant_id=? GROUP BY c.id, c.channel_name
                        """, from, to, tid)) {
                    sb.append(csv(r.get("channel_name"))).append(',').append(r.get("orders")).append(',')
                            .append(r.get("amount")).append(',').append(r.get("commission")).append('\n');
                }
            }
            case "commission" -> {
                sb.append("员工,订单号,类型,基数,比例,金额,状态,时间\n");
                for (Map<String, Object> r : jdbc.queryForList("""
                        SELECT c.*, e.real_name, o.order_no FROM commission_record c
                        LEFT JOIN employee e ON e.id=c.employee_id AND e.tenant_id=c.tenant_id
                        LEFT JOIN order_main o ON o.id=c.order_id AND o.tenant_id=c.tenant_id
                        WHERE c.tenant_id=? AND DATE(c.created_at) BETWEEN ? AND ? ORDER BY c.id
                        """, tid, from, to)) {
                    sb.append(csv(r.get("real_name"))).append(',').append(csv(r.get("order_no"))).append(',')
                            .append(csv(r.get("commission_type"))).append(',').append(r.get("base_amount")).append(',')
                            .append(r.get("rate")).append(',').append(r.get("amount")).append(',')
                            .append(csv(r.get("status"))).append(',').append(csv(r.get("created_at"))).append('\n');
                }
            }
            default -> {
                sb.append("指标,数值\n");
                Map<String, Object> overview = overview(from, to);
                overview.forEach((k, v) -> {
                    if (!(v instanceof List)) sb.append(csv(k)).append(',').append(csv(v)).append('\n');
                });
            }
        }
        return sb.toString();
    }

    private String csv(Object value) {
        if (value == null) return "";
        String s = String.valueOf(value);
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    public PageResult<Map<String, Object>> tasks(long page, long size) {
        Long tid = TenantContext.requireTenantId();
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM report_task WHERE tenant_id=?", Long.class, tid);
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT * FROM report_task WHERE tenant_id=? ORDER BY id DESC LIMIT ? OFFSET ?
                """, tid, size, (page - 1) * size);
        return PageResult.of(total == null ? 0 : total, page, size, rows);
    }

    public Map<String, Object> download(Long taskId) {
        Long tid = TenantContext.requireTenantId();
        Map<String, Object> task = jdbc.queryForMap("SELECT * FROM report_task WHERE tenant_id=? AND id=?", tid, taskId);
        if (!"DONE".equals(task.get("status"))) throw new BizException("报表尚未生成完成");
        return Map.of("url", storageManager.signedUrl(tid, storageManager.activeConfig(tid).getProvider(),
                String.valueOf(task.get("file_url")), 30), "task", task);
    }
}
