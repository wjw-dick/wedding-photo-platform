package com.wedding.biz.supplier;

import com.wedding.common.api.BizException;
import com.wedding.common.api.PageResult;
import com.wedding.common.context.LoginContext;
import com.wedding.common.context.TenantContext;
import com.wedding.common.security.JwtService;
import com.wedding.common.util.BizNo;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class SupplierService {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final BizNo bizNo;

    public Map<String, Object> login(String tenantCode, String username, String password) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT a.*, s.supplier_name, t.tenant_code FROM supplier_account a
                JOIN supplier s ON s.id=a.supplier_id AND s.tenant_id=a.tenant_id
                JOIN platform_tenant t ON t.tenant_id=a.tenant_id
                WHERE t.tenant_code=? AND a.username=?
                """, tenantCode, username);
        if (rows.isEmpty() || !passwordEncoder.matches(password, String.valueOf(rows.get(0).get("password")))) {
            throw new BizException(401, "账号或密码错误");
        }
        Map<String, Object> row = rows.get(0);
        if (((Number) row.get("status")).intValue() != 1) throw new BizException(403, "账号已停用");
        Long tid = ((Number) row.get("tenant_id")).longValue();
        Long supplierId = ((Number) row.get("supplier_id")).longValue();
        jdbc.update("UPDATE supplier_account SET last_login_at=NOW() WHERE id=?", row.get("id"));
        String token = jwtService.issue(supplierId, tid, tenantCode, username, "SUPPLIER", null, null);
        return Map.of("token", token, "supplierId", supplierId, "supplierName", row.get("supplier_name"),
                "tenantCode", tenantCode, "userType", "SUPPLIER");
    }

    public PageResult<Map<String, Object>> myOrders(String status, long page, long size) {
        Long tid = TenantContext.requireTenantId();
        Long supplierId = LoginContext.require().getId();
        String where = " WHERE p.tenant_id=? AND p.supplier_id=?" + (status == null || status.isBlank() ? "" : " AND p.status=?");
        List<Object> args = new ArrayList<>(List.of(tid, supplierId));
        if (status != null && !status.isBlank()) args.add(status);
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM product_order p" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add((page - 1) * size);
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT p.*, o.order_no, o.customer_name, o.customer_phone, o.shoot_date, pk.name AS package_name
                FROM product_order p
                LEFT JOIN order_main o ON o.id=p.order_id AND o.tenant_id=p.tenant_id
                LEFT JOIN pkg_package pk ON pk.id=o.package_id AND pk.tenant_id=o.tenant_id
                """ + where + " ORDER BY p.id DESC LIMIT ? OFFSET ?", pageArgs.toArray());
        return PageResult.of(total == null ? 0 : total, page, size, rows);
    }

    @Transactional
    public void action(Long productId, String action, String reason, String trackingCompany, String trackingNo) {
        Long tid = TenantContext.requireTenantId();
        Long supplierId = LoginContext.require().getId();
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT * FROM product_order WHERE tenant_id=? AND id=? AND supplier_id=?", tid, productId, supplierId);
        String status = switch (action) {
            case "ACCEPT" -> "ACCEPTED";
            case "REJECT" -> {
                if (reason == null || reason.isBlank()) throw new BizException("拒单必须填写原因");
                yield "REJECTED";
            }
            case "PRODUCING" -> "PRODUCING";
            case "DONE" -> "DONE";
            case "SHIP" -> {
                if (trackingNo == null || trackingNo.isBlank()) throw new BizException("请填写物流单号");
                yield "SHIPPED";
            }
            case "RECEIVED" -> "RECEIVED";
            default -> throw new BizException("不支持的操作");
        };
        if ("REJECTED".equals(row.get("status")) || "RECEIVED".equals(row.get("status"))) {
            throw new BizException("当前状态不可操作");
        }
        jdbc.update("""
                UPDATE product_order SET status=?, reject_reason=?, tracking_company=?, tracking_no=?
                WHERE tenant_id=? AND id=?
                """, status, reason, trackingCompany, trackingNo, tid, productId);
        jdbc.update("INSERT INTO sys_notice(tenant_id, title, content, channel) VALUES (?,?,?, 'SITE')",
                tid, "供应商进度回传", "制作单 " + row.get("product_no") + " 状态更新为 " + status);
    }

    public Map<String, Object> reconciliation(String period) {
        Long tid = TenantContext.requireTenantId();
        Long supplierId = LoginContext.require().getId();
        if (period == null || period.isBlank()) period = LocalDate.now().toString().substring(0, 7);
        List<Map<String, Object>> orders = jdbc.queryForList("""
                SELECT p.*, o.order_no, o.total_amount FROM product_order p
                JOIN order_main o ON o.id=p.order_id AND o.tenant_id=p.tenant_id
                WHERE p.tenant_id=? AND p.supplier_id=? AND DATE_FORMAT(p.created_at,'%Y-%m')=?
                """, tid, supplierId, period);
        BigDecimal payable = orders.stream()
                .map(o -> o.get("total_amount") == null ? BigDecimal.ZERO : ((BigDecimal) o.get("total_amount")).multiply(new BigDecimal("0.35")))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<Map<String, Object>> settlements = jdbc.queryForList("""
                SELECT * FROM settlement WHERE tenant_id=? AND target_type='SUPPLIER' AND target_id=? ORDER BY id DESC
                """, tid, supplierId);
        return Map.of("period", period, "orders", orders, "payable", payable, "settlements", settlements);
    }

    public List<Map<String, Object>> qualifications() {
        Long tid = TenantContext.requireTenantId();
        Long supplierId = LoginContext.require().getId();
        return jdbc.queryForList("""
                SELECT *, DATEDIFF(expire_date, CURDATE()) AS days_left FROM supplier_qualification
                WHERE tenant_id=? AND supplier_id=? ORDER BY expire_date
                """, tid, supplierId);
    }

    @Transactional
    public Long addQualification(String qualType, String fileUrl, String expireDate) {
        Long tid = TenantContext.requireTenantId();
        Long supplierId = LoginContext.require().getId();
        org.springframework.jdbc.support.KeyHolder kh = new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbc.update(con -> {
            var ps = con.prepareStatement("""
                    INSERT INTO supplier_qualification(tenant_id, supplier_id, qual_type, file_url, expire_date)
                    VALUES (?,?,?,?,?)
                    """, java.sql.Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, tid);
            ps.setLong(2, supplierId);
            ps.setString(3, qualType);
            ps.setString(4, fileUrl);
            ps.setString(5, expireDate);
            return ps;
        }, kh);
        return Objects.requireNonNull(kh.getKey()).longValue();
    }

    // ---------------- 租户后台 ----------------

    public PageResult<Map<String, Object>> suppliers(String keyword, long page, long size) {
        Long tid = TenantContext.requireTenantId();
        String where = " WHERE s.tenant_id=? AND s.deleted=0" +
                (keyword == null || keyword.isBlank() ? "" : " AND (s.supplier_name LIKE ? OR s.supplier_no LIKE ?)");
        List<Object> args = new ArrayList<>(List.of(tid));
        if (keyword != null && !keyword.isBlank()) {
            args.add("%" + keyword + "%");
            args.add("%" + keyword + "%");
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM supplier s" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add((page - 1) * size);
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT s.*, (SELECT COUNT(*) FROM product_order p WHERE p.tenant_id=s.tenant_id AND p.supplier_id=s.id) AS order_count,
                       (SELECT COUNT(*) FROM supplier_qualification q WHERE q.tenant_id=s.tenant_id AND q.supplier_id=s.id
                         AND q.expire_date <= DATE_ADD(CURDATE(), INTERVAL 30 DAY)) AS expiring_count
                FROM supplier s
                """ + where + " ORDER BY s.id DESC LIMIT ? OFFSET ?", pageArgs.toArray());
        return PageResult.of(total == null ? 0 : total, page, size, rows);
    }

    @Transactional
    public Map<String, Object> createAccount(Long supplierId, String username, String password) {
        Long tid = TenantContext.requireTenantId();
        if (password == null || password.length() < 6) throw new BizException("密码至少 6 位");
        jdbc.update("""
                INSERT INTO supplier_account(tenant_id, supplier_id, username, password, status)
                VALUES (?,?,?,?,1)
                """, tid, supplierId, username, passwordEncoder.encode(password));
        return Map.of("username", username, "supplierId", supplierId);
    }

    public Map<String, Object> monthlyStatement(String period) {
        Long tid = TenantContext.requireTenantId();
        if (period == null || period.isBlank()) period = LocalDate.now().toString().substring(0, 7);
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT s.id AS supplier_id, s.supplier_name, COUNT(p.id) AS order_count,
                       COALESCE(SUM(o.total_amount),0) AS order_amount,
                       COALESCE(SUM(o.total_amount),0) * 0.35 AS payable
                FROM supplier s
                LEFT JOIN product_order p ON p.supplier_id=s.id AND p.tenant_id=s.tenant_id
                     AND DATE_FORMAT(p.created_at,'%Y-%m')=?
                LEFT JOIN order_main o ON o.id=p.order_id AND o.tenant_id=p.tenant_id
                WHERE s.tenant_id=? AND s.deleted=0
                GROUP BY s.id, s.supplier_name
                """, period, tid);
        return Map.of("period", period, "rows", rows);
    }

    public Map<String, Object> expiringQualifications() {
        Long tid = TenantContext.requireTenantId();
        return Map.of("rows", jdbc.queryForList("""
                SELECT q.*, s.supplier_name, DATEDIFF(q.expire_date, CURDATE()) AS days_left
                FROM supplier_qualification q JOIN supplier s ON s.id=q.supplier_id AND s.tenant_id=q.tenant_id
                WHERE q.tenant_id=? AND q.expire_date <= DATE_ADD(CURDATE(), INTERVAL 30 DAY)
                ORDER BY q.expire_date
                """, tid));
    }
}
