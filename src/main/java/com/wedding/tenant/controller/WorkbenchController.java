package com.wedding.tenant.controller;

import com.wedding.common.api.R;
import com.wedding.common.context.TenantContext;
import com.wedding.common.security.RequirePermission;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tenant/workbench")
@RequiredArgsConstructor
public class WorkbenchController {
    private final JdbcTemplate jdbc;

    @GetMapping
    @RequirePermission("dashboard:read")
    public R<Map<String, Object>> workbench() {
        Long tid = TenantContext.requireTenantId();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("todaySchedules", jdbc.queryForList("""
                SELECT t.id, t.task_no, t.shoot_date, t.gather_time, t.gather_place,
                       o.customer_name, o.order_no
                FROM shoot_task t LEFT JOIN order_main o ON o.id=t.order_id AND o.tenant_id=t.tenant_id
                WHERE t.tenant_id=? AND t.shoot_date=CURDATE() AND t.deleted=0 ORDER BY t.gather_time
                """, tid));
        m.put("pendingOrderConfirm", count("SELECT COUNT(*) FROM order_main WHERE tenant_id=? AND status IN ('LEAD','ARRIVED') AND deleted=0", tid));
        m.put("pendingDispatch", count("SELECT COUNT(*) FROM shoot_task WHERE tenant_id=? AND status='PENDING' AND deleted=0", tid));
        m.put("pendingSelection", count("SELECT COUNT(*) FROM selection_sheet WHERE tenant_id=? AND status IN ('OPEN','SUBMITTED')", tid));
        m.put("pendingDelivery", count("SELECT COUNT(*) FROM delivery_record WHERE tenant_id=? AND status IN ('PENDING','NOTIFIED')", tid));
        m.put("retentionWarnings", count("SELECT COUNT(*) FROM delivery_record WHERE tenant_id=? AND retain_end BETWEEN CURDATE() AND DATE_ADD(CURDATE(), INTERVAL 7 DAY)", tid));
        m.put("pendingChannelSettle", count("SELECT COUNT(*) FROM settlement WHERE tenant_id=? AND status='PENDING'", tid));
        m.put("storeTop5", jdbc.queryForList("""
                SELECT s.store_name, COUNT(o.id) AS order_count, COALESCE(SUM(o.paid_amount),0) AS amount
                FROM sys_store s LEFT JOIN order_main o ON o.store_id=s.id AND o.tenant_id=s.tenant_id AND o.deleted=0
                WHERE s.tenant_id=? AND s.deleted=0
                GROUP BY s.id, s.store_name ORDER BY amount DESC LIMIT 5
                """, tid));
        m.put("exceptions", jdbc.queryForList("""
                SELECT order_no, customer_name, abnormal_status, status, updated_at
                FROM order_main WHERE tenant_id=? AND deleted=0 AND abnormal_status IS NOT NULL
                ORDER BY updated_at DESC LIMIT 10
                """, tid));
        return R.ok(m);
    }

    private long count(String sql, Object... args) {
        Long v = jdbc.queryForObject(sql, Long.class, args);
        return v == null ? 0 : v;
    }
}
