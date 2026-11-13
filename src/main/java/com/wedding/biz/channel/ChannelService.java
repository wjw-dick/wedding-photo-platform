package com.wedding.biz.channel;

import com.wedding.common.api.BizException;
import com.wedding.common.api.PageResult;
import com.wedding.common.context.LoginContext;
import com.wedding.common.context.TenantContext;
import com.wedding.common.util.BizNo;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/** 渠道报单、归属保护、佣金与结算。 */
@Service
@RequiredArgsConstructor
public class ChannelService {
    private final JdbcTemplate jdbc;
    private final BizNo bizNo;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    private final com.wedding.common.security.JwtService jwtService;

    public Map<String, Object> login(String tenantCode, String username, String password) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT a.*, c.channel_name, t.tenant_code FROM channel_account a
                JOIN channel c ON c.id=a.channel_id AND c.tenant_id=a.tenant_id
                JOIN platform_tenant t ON t.tenant_id=a.tenant_id
                WHERE t.tenant_code=? AND a.username=?
                """, tenantCode, username);
        if (rows.isEmpty() || !passwordEncoder.matches(password, String.valueOf(rows.get(0).get("password")))) {
            throw new BizException(401, "账号或密码错误");
        }
        Map<String, Object> row = rows.get(0);
        if (((Number) row.get("status")).intValue() != 1) throw new BizException(403, "账号已停用");
        Long tid = ((Number) row.get("tenant_id")).longValue();
        Long channelId = ((Number) row.get("channel_id")).longValue();
        jdbc.update("UPDATE channel_account SET last_login_at=NOW() WHERE id=?", row.get("id"));
        String token = jwtService.issue(channelId, tid, tenantCode, username, "CHANNEL_USER", null, null);
        return Map.of("token", token, "channelId", channelId, "channelName", row.get("channel_name"),
                "tenantCode", tenantCode, "userType", "CHANNEL_USER");
    }

    public PageResult<Map<String, Object>> channels(String keyword, Long parentId, long page, long size) {
        Long tid = TenantContext.requireTenantId();
        StringBuilder where = new StringBuilder(" WHERE c.tenant_id=? AND c.deleted=0");
        List<Object> args = new ArrayList<>();
        args.add(tid);
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (c.channel_name LIKE ? OR c.channel_no LIKE ? OR c.contact_phone LIKE ?)");
            for (int i = 0; i < 3; i++) args.add("%" + keyword + "%");
        }
        if (parentId != null) {
            where.append(" AND c.parent_id=?");
            args.add(parentId);
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM channel c" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add((page - 1) * size);
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT c.*, p.channel_name AS parent_name,
                       (SELECT COUNT(*) FROM channel_report r WHERE r.tenant_id=c.tenant_id AND r.channel_id=c.id) AS report_count
                FROM channel c LEFT JOIN channel p ON p.id=c.parent_id AND p.tenant_id=c.tenant_id
                """ + where + " ORDER BY c.id DESC LIMIT ? OFFSET ?", pageArgs.toArray());
        return PageResult.of(total == null ? 0 : total, page, size, rows);
    }

    @Transactional
    public Map<String, Object> submitReport(Long channelId, Map<String, Object> body) {
        Long tid = TenantContext.requireTenantId();
        Map<String, Object> channel;
        try {
            channel = jdbc.queryForMap("SELECT * FROM channel WHERE tenant_id=? AND id=? AND deleted=0", tid, channelId);
        } catch (Exception e) {
            throw BizException.notFound("渠道商不存在");
        }
        if (((Number) channel.get("status")).intValue() != 1) throw new BizException("渠道商已停用");
        String phone = String.valueOf(body.getOrDefault("customerPhone", ""));
        if (!phone.matches("^1\\d{10}$")) throw new BizException("请输入正确手机号");
        List<Map<String, Object>> existing = jdbc.queryForList("""
                SELECT r.*, c.channel_name FROM channel_report r
                JOIN channel c ON c.id=r.channel_id AND c.tenant_id=r.tenant_id
                WHERE r.tenant_id=? AND r.customer_phone=? AND r.status IN ('PENDING','APPROVED')
                  AND (r.protect_until IS NULL OR r.protect_until > NOW())
                LIMIT 1
                """, tid, phone);
        if (!existing.isEmpty()) {
            Map<String, Object> row = existing.get(0);
            if (((Number) row.get("channel_id")).longValue() == channelId) {
                return Map.of("duplicate", true, "reportNo", row.get("report_no"), "message", "该客户已由您报备，保护期内无需重复提交");
            }
            throw new BizException(409, "该客户已在保护期内归属渠道【" + row.get("channel_name") + "】，不可重复报单");
        }
        int protectDays = 30;
        try {
            Integer d = jdbc.queryForObject("SELECT CAST(param_value AS UNSIGNED) FROM sys_param WHERE tenant_id=? AND param_key='channel.protect.days'",
                    Integer.class, tid);
            if (d != null) protectDays = d;
        } catch (Exception ignored) {
        }
        String reportNo = bizNo.next("CR");
        jdbc.update("""
                INSERT INTO channel_report(tenant_id, channel_id, customer_name, customer_phone, package_id, store_id,
                                           report_no, status, protect_until)
                VALUES (?,?,?,?,?,?,?, 'PENDING', ?)
                """, tid, channelId, body.get("customerName"), phone, body.get("packageId"), body.get("storeId"),
                reportNo, LocalDateTime.now().plusDays(protectDays));
        return Map.of("duplicate", false, "reportNo", reportNo, "protectUntil", LocalDateTime.now().plusDays(protectDays));
    }

    public PageResult<Map<String, Object>> reports(String keyword, String status, Long channelId, long page, long size) {
        Long tid = TenantContext.requireTenantId();
        StringBuilder where = new StringBuilder(" WHERE r.tenant_id=?");
        List<Object> args = new ArrayList<>();
        args.add(tid);
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (r.customer_name LIKE ? OR r.customer_phone LIKE ? OR r.report_no LIKE ?)");
            for (int i = 0; i < 3; i++) args.add("%" + keyword + "%");
        }
        if (status != null && !status.isBlank()) {
            where.append(" AND r.status=?");
            args.add(status);
        }
        if (channelId != null) {
            where.append(" AND r.channel_id=?");
            args.add(channelId);
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM channel_report r" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add((page - 1) * size);
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT r.*, c.channel_name, p.name AS package_name, s.store_name
                FROM channel_report r
                LEFT JOIN channel c ON c.id=r.channel_id AND c.tenant_id=r.tenant_id
                LEFT JOIN pkg_package p ON p.id=r.package_id AND p.tenant_id=r.tenant_id
                LEFT JOIN sys_store s ON s.id=r.store_id AND s.tenant_id=r.tenant_id
                """ + where + " ORDER BY r.id DESC LIMIT ? OFFSET ?", pageArgs.toArray());
        return PageResult.of(total == null ? 0 : total, page, size, rows);
    }

    @Transactional
    public void auditReport(Long reportId, boolean approve, String remark) {
        Long tid = TenantContext.requireTenantId();
        Map<String, Object> report = jdbc.queryForMap("SELECT * FROM channel_report WHERE tenant_id=? AND id=?", tid, reportId);
        if (!"PENDING".equals(report.get("status"))) throw new BizException("报单已审核");
        jdbc.update("UPDATE channel_report SET status=? WHERE tenant_id=? AND id=?",
                approve ? "APPROVED" : "REJECTED", tid, reportId);
        if (approve) {
            jdbc.update("""
                    INSERT INTO crm_lead(tenant_id, store_id, name, phone, source, channel_id, package_id, status, protect_until)
                    VALUES (?,?,?,?, 'CHANNEL', ?,?, 'NEW', ?)
                    """, tid, report.get("store_id"), report.get("customer_name"), report.get("customer_phone"),
                    report.get("channel_id"), report.get("package_id"), report.get("protect_until"));
        }
        if (remark != null && !remark.isBlank()) {
            jdbc.update("INSERT INTO sys_notice(tenant_id, title, content, channel) VALUES (?,?,?, 'SITE')",
                    tid, "渠道报单审核", "报单 " + report.get("report_no") + " 审核结果：" + remark);
        }
    }

    /** 订单结算时按快照规则生成佣金明细，已存在则跳过。 */
    @Transactional
    public Map<String, Object> settleOrder(Long orderId) {
        Long tid = TenantContext.requireTenantId();
        Map<String, Object> order = jdbc.queryForMap("SELECT * FROM order_main WHERE tenant_id=? AND id=?", tid, orderId);
        Long exists = jdbc.queryForObject("SELECT COUNT(*) FROM commission_record WHERE tenant_id=? AND order_id=?",
                Long.class, tid, orderId);
        if (exists != null && exists > 0) {
            return Map.of("exists", true, "message", "该订单佣金已生成");
        }
        BigDecimal base = (BigDecimal) order.get("total_amount");
        Long channelId = order.get("channel_id") == null ? null : ((Number) order.get("channel_id")).longValue();
        if (channelId == null) return Map.of("exists", false, "message", "订单无渠道归属");
        Map<String, Object> channel = jdbc.queryForMap("SELECT * FROM channel WHERE tenant_id=? AND id=?", tid, channelId);
        BigDecimal rate = (BigDecimal) channel.get("commission_rate");
        BigDecimal amount = base.multiply(rate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        jdbc.update("""
                INSERT INTO commission_record(tenant_id, order_id, channel_id, commission_type, rule_snapshot,
                                              base_amount, rate, amount, status)
                VALUES (?,?,?, 'CHANNEL', ?,?,?,?, 'PENDING')
                """, tid, orderId, channelId, "渠道 " + channel.get("channel_name") + " 比例 " + rate + "%",
                base, rate, amount);
        return Map.of("exists", false, "amount", amount);
    }

    public PageResult<Map<String, Object>> commissions(String status, Long channelId, long page, long size) {
        Long tid = TenantContext.requireTenantId();
        StringBuilder where = new StringBuilder(" WHERE c.tenant_id=?");
        List<Object> args = new ArrayList<>();
        args.add(tid);
        if (status != null && !status.isBlank()) {
            where.append(" AND c.status=?");
            args.add(status);
        }
        if (channelId != null) {
            where.append(" AND c.channel_id=?");
            args.add(channelId);
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM commission_record c" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add((page - 1) * size);
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT c.*, o.order_no, o.customer_name, ch.channel_name, e.real_name AS employee_name
                FROM commission_record c
                LEFT JOIN order_main o ON o.id=c.order_id AND o.tenant_id=c.tenant_id
                LEFT JOIN channel ch ON ch.id=c.channel_id AND ch.tenant_id=c.tenant_id
                LEFT JOIN employee e ON e.id=c.employee_id AND e.tenant_id=c.tenant_id
                """ + where + " ORDER BY c.id DESC LIMIT ? OFFSET ?", pageArgs.toArray());
        return PageResult.of(total == null ? 0 : total, page, size, rows);
    }

    @Transactional
    public Map<String, Object> generateSettlement(String targetType, Long targetId, String period) {
        Long tid = TenantContext.requireTenantId();
        if (period == null || period.isBlank()) period = LocalDate.now().toString().substring(0, 7);
        String column = "CHANNEL".equals(targetType) ? "channel_id" : "employee_id";
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT * FROM commission_record
                WHERE tenant_id=? AND """ + column + "=? AND status='PENDING'",
                tid, targetId);
        BigDecimal sum = rows.stream().map(r -> (BigDecimal) r.get("amount"))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        String no = bizNo.next("ST");
        jdbc.update("""
                INSERT INTO settlement(tenant_id, settlement_no, target_type, target_id, period, amount, status)
                VALUES (?,?,?,?,?,?, 'PENDING')
                """, tid, no, targetType, targetId, period, sum);
        for (Map<String, Object> row : rows) {
            jdbc.update("UPDATE commission_record SET status='SETTLED' WHERE tenant_id=? AND id=?", tid, row.get("id"));
            jdbc.update("""
                    INSERT INTO commission_detail(tenant_id, commission_record_id, employee_id, rule_snapshot, amount, settle_status)
                    VALUES (?,?,?,?,?, 'SETTLED')
                    """, tid, row.get("id"), row.get("employee_id"), row.get("rule_snapshot"), row.get("amount"));
        }
        return Map.of("settlementNo", no, "amount", sum, "count", rows.size());
    }

    public Map<String, Object> createAccount(Long channelId, String username, String password) {
        Long tid = TenantContext.requireTenantId();
        if (password == null || password.length() < 6) throw new BizException("密码至少 6 位");
        jdbc.update("""
                INSERT INTO channel_account(tenant_id, channel_id, username, password, status)
                VALUES (?,?,?,?,1)
                """, tid, channelId, username, passwordEncoder.encode(password));
        return Map.of("username", username, "channelId", channelId);
    }

    public List<Map<String, Object>> settlements(String targetType, Long targetId) {        Long tid = TenantContext.requireTenantId();
        return jdbc.queryForList("""
                SELECT * FROM settlement WHERE tenant_id=? AND (? IS NULL OR target_type=?)
                  AND (? IS NULL OR target_id=?) ORDER BY id DESC
                """, tid, targetType, targetType, targetId, targetId);
    }

    @Transactional
    public void paySettlement(Long settlementId) {
        Long tid = TenantContext.requireTenantId();
        jdbc.update("UPDATE settlement SET status='PAID', paid_at=NOW() WHERE tenant_id=? AND id=? AND status='PENDING'",
                tid, settlementId);
    }

    public List<Map<String, Object>> subChannels(Long channelId) {
        Long tid = TenantContext.requireTenantId();
        List<Map<String, Object>> level1 = jdbc.queryForList(
                "SELECT * FROM channel WHERE tenant_id=? AND parent_id=? AND deleted=0", tid, channelId);
        List<Map<String, Object>> result = new ArrayList<>(level1);
        for (Map<String, Object> c : level1) {
            result.addAll(jdbc.queryForList(
                    "SELECT * FROM channel WHERE tenant_id=? AND parent_id=? AND deleted=0", tid, c.get("id")));
        }
        return result;
    }

    /** 渠道商首页：本月报单/成交/佣金预估/额度。 */
    public Map<String, Object> dashboard(Long channelId) {
        Long tid = TenantContext.requireTenantId();
        Map<String, Object> channel = jdbc.queryForMap("SELECT * FROM channel WHERE tenant_id=? AND id=?", tid, channelId);
        String month = LocalDate.now().toString().substring(0, 7);
        Long reports = jdbc.queryForObject("""
                SELECT COUNT(*) FROM channel_report WHERE tenant_id=? AND channel_id=? AND DATE_FORMAT(created_at,'%Y-%m')=?
                """, Long.class, tid, channelId, month);
        Long deals = jdbc.queryForObject("""
                SELECT COUNT(*) FROM channel_report WHERE tenant_id=? AND channel_id=? AND status='APPROVED'
                  AND DATE_FORMAT(created_at,'%Y-%m')=?
                """, Long.class, tid, channelId, month);
        BigDecimal commission = jdbc.queryForObject("""
                SELECT COALESCE(SUM(amount),0) FROM commission_record WHERE tenant_id=? AND channel_id=?
                """, BigDecimal.class, tid, channelId);
        BigDecimal settled = jdbc.queryForObject("""
                SELECT COALESCE(SUM(amount),0) FROM settlement WHERE tenant_id=? AND target_type='CHANNEL' AND target_id=?
                """, BigDecimal.class, tid, channelId);
        return Map.of("channel", channel, "monthReports", reports, "monthDeals", deals,
                "commissionTotal", commission, "settledTotal", settled,
                "creditLimit", channel.get("credit_limit"));
    }
}
