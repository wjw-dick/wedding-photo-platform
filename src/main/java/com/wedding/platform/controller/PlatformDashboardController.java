package com.wedding.platform.controller;

import com.wedding.common.api.R;
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
@RequestMapping("/api/platform/dashboard")
@RequiredArgsConstructor
public class PlatformDashboardController {
    private final JdbcTemplate jdbc;

    @GetMapping
    @RequirePermission("dashboard:read")
    public R<Map<String, Object>> dashboard() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tenantTotal", count("SELECT COUNT(*) FROM platform_tenant WHERE deleted=0"));
        m.put("activeTenants", count("SELECT COUNT(*) FROM platform_tenant WHERE status='ACTIVE' AND deleted=0"));
        m.put("trialTenants", count("SELECT COUNT(*) FROM platform_tenant WHERE status='TRIAL' AND deleted=0"));
        m.put("overdueTenants", count("SELECT COUNT(*) FROM platform_tenant WHERE status='OVERDUE' AND deleted=0"));
        m.put("expiringIn30Days", count("SELECT COUNT(*) FROM platform_tenant WHERE deleted=0 AND expire_at BETWEEN NOW() AND DATE_ADD(NOW(), INTERVAL 30 DAY)"));
        m.put("totalSeats", count("SELECT COALESCE(SUM(seat_limit),0) FROM platform_tenant WHERE deleted=0"));
        m.put("trialConversion", jdbc.queryForObject("""
                SELECT ROUND(SUM(status='ACTIVE') / NULLIF(COUNT(*),0) * 100, 1)
                FROM platform_tenant WHERE deleted=0
                """, Double.class));
        m.put("revenueTrend", jdbc.queryForList("""
                SELECT bill_period AS period, SUM(amount) AS amount
                FROM platform_bill WHERE deleted=0 AND status='PAID'
                GROUP BY bill_period ORDER BY bill_period DESC LIMIT 6
                """));
        m.put("recentAudits", jdbc.queryForList("""
                SELECT operator_name, action, target_tenant_id, result, created_at
                FROM platform_audit ORDER BY id DESC LIMIT 10
                """));
        return R.ok(m);
    }

    private long count(String sql) {
        Long v = jdbc.queryForObject(sql, Long.class);
        return v == null ? 0 : v;
    }
}
