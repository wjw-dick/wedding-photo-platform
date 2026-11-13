package com.wedding.biz.employee;

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
import java.util.*;

/** 员工排班、计件规则、提成明细与业绩。 */
@Service
@RequiredArgsConstructor
public class EmployeeService {
    private final JdbcTemplate jdbc;
    private final BizNo bizNo;

    public PageResult<Map<String, Object>> employees(String keyword, String roleType, long page, long size) {
        Long tid = TenantContext.requireTenantId();
        StringBuilder where = new StringBuilder(" WHERE e.tenant_id=? AND e.deleted=0");
        List<Object> args = new ArrayList<>(List.of(tid));
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (e.real_name LIKE ? OR e.employee_no LIKE ? OR e.phone LIKE ?)");
            for (int i = 0; i < 3; i++) args.add("%" + keyword + "%");
        }
        if (roleType != null && !roleType.isBlank()) {
            where.append(" AND e.role_type=?");
            args.add(roleType);
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM employee e" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add((page - 1) * size);
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT e.*, s.store_name, u.username,
                       (SELECT COUNT(*) FROM shoot_task t WHERE t.tenant_id=e.tenant_id AND t.photographer_id=e.id) AS shoot_count
                FROM employee e
                LEFT JOIN sys_store s ON s.id=e.store_id AND s.tenant_id=e.tenant_id
                LEFT JOIN sys_user u ON u.id=e.user_id AND u.tenant_id=e.tenant_id
                """ + where + " ORDER BY e.id DESC LIMIT ? OFFSET ?", pageArgs.toArray());
        return PageResult.of(total == null ? 0 : total, page, size, rows);
    }

    public List<Map<String, Object>> schedules(String from, String to, Long storeId) {
        Long tid = TenantContext.requireTenantId();
        if (from == null || from.isBlank()) from = LocalDate.now().toString();
        if (to == null || to.isBlank()) to = LocalDate.now().plusDays(7).toString();
        return jdbc.queryForList("""
                SELECT es.*, e.real_name, e.role_type, s.store_name
                FROM employee_schedule es
                JOIN employee e ON e.id=es.employee_id AND e.tenant_id=es.tenant_id
                LEFT JOIN sys_store s ON s.id=es.store_id AND s.tenant_id=es.tenant_id
                WHERE es.tenant_id=? AND es.work_date BETWEEN ? AND ? AND (? IS NULL OR es.store_id=?)
                ORDER BY es.work_date, e.real_name
                """, tid, from, to, storeId, storeId);
    }

    @Transactional
    public void saveSchedule(Long employeeId, String workDate, String shift, Long storeId, Integer status) {
        Long tid = TenantContext.requireTenantId();
        Long exists = jdbc.queryForObject("""
                SELECT COUNT(*) FROM employee_schedule WHERE tenant_id=? AND employee_id=? AND work_date=?
                """, Long.class, tid, employeeId, workDate);
        if (exists != null && exists > 0) {
            jdbc.update("""
                    UPDATE employee_schedule SET shift=?, store_id=COALESCE(?,store_id), status=COALESCE(?,status)
                    WHERE tenant_id=? AND employee_id=? AND work_date=?
                    """, shift, storeId, status, tid, employeeId, workDate);
        } else {
            jdbc.update("""
                    INSERT INTO employee_schedule(tenant_id, employee_id, store_id, work_date, shift, status)
                    VALUES (?,?,?,?,?,?)
                    """, tid, employeeId, storeId, workDate, shift, status == null ? 1 : status);
        }
    }

    public List<Map<String, Object>> rules() {
        Long tid = TenantContext.requireTenantId();
        return jdbc.queryForList("SELECT * FROM commission_rule WHERE tenant_id=? ORDER BY id", tid);
    }

    @Transactional
    public Long saveRule(Map<String, Object> body) {
        Long tid = TenantContext.requireTenantId();
        if (body.get("id") != null) {
            jdbc.update("""
                    UPDATE commission_rule SET rule_name=?, rule_type=?, base_type=?, rate=?, fixed_amount=?,
                                               ladder_json=?, status=?
                    WHERE tenant_id=? AND id=?
                    """, body.get("ruleName"), body.get("ruleType"), body.get("baseType"), body.get("rate"),
                    body.get("fixedAmount"), body.get("ladderJson"),
                    body.get("status") == null ? 1 : body.get("status"), tid, body.get("id"));
            return Long.valueOf(String.valueOf(body.get("id")));
        }
        org.springframework.jdbc.support.KeyHolder kh = new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbc.update(con -> {
            var ps = con.prepareStatement("""
                    INSERT INTO commission_rule(tenant_id, rule_name, rule_type, base_type, rate, fixed_amount, ladder_json, status)
                    VALUES (?,?,?,?,?,?,?,?)
                    """, java.sql.Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, tid);
            ps.setString(2, String.valueOf(body.get("ruleName")));
            ps.setString(3, String.valueOf(body.get("ruleType")));
            ps.setString(4, String.valueOf(body.getOrDefault("baseType", "ORDER_AMOUNT")));
            ps.setBigDecimal(5, new BigDecimal(String.valueOf(body.getOrDefault("rate", "0"))));
            ps.setBigDecimal(6, new BigDecimal(String.valueOf(body.getOrDefault("fixedAmount", "0"))));
            ps.setString(7, (String) body.get("ladderJson"));
            ps.setInt(8, Integer.parseInt(String.valueOf(body.getOrDefault("status", "1"))));
            return ps;
        }, kh);
        return Objects.requireNonNull(kh.getKey()).longValue();
    }

    /** 订单成交后按规则生成提成（门市、拍摄、修片、加购）。 */
    @Transactional
    public Map<String, Object> calculateForOrder(Long orderId) {
        Long tid = TenantContext.requireTenantId();
        Map<String, Object> order = jdbc.queryForMap("SELECT * FROM order_main WHERE tenant_id=? AND id=?", tid, orderId);
        BigDecimal base = (BigDecimal) order.get("total_amount");
        int created = 0;
        Long salesmanId = order.get("salesman_id") == null ? null : ((Number) order.get("salesman_id")).longValue();
        if (salesmanId != null) {
            Long exists = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM commission_record WHERE tenant_id=? AND order_id=? AND employee_id=? AND commission_type='SALES'
                    """, Long.class, tid, orderId, salesmanId);
            if (exists == null || exists == 0) {
                List<Map<String, Object>> rules = jdbc.queryForList(
                        "SELECT * FROM commission_rule WHERE tenant_id=? AND rule_type='SALES' AND status=1 LIMIT 1", tid);
                BigDecimal rate = rules.isEmpty() ? new BigDecimal("2") : (BigDecimal) rules.get(0).get("rate");
                BigDecimal amount = base.multiply(rate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                jdbc.update("""
                        INSERT INTO commission_record(tenant_id, order_id, employee_id, commission_type, rule_snapshot,
                                                      base_amount, rate, amount, status)
                        VALUES (?,?,?, 'SALES', ?,?,?,?, 'PENDING')
                        """, tid, orderId, salesmanId, "门市成单比例 " + rate + "%", base, rate, amount);
                created++;
            }
        }
        List<Map<String, Object>> shootTasks = jdbc.queryForList("""
                SELECT DISTINCT photographer_id FROM shoot_task WHERE tenant_id=? AND order_id=? AND photographer_id IS NOT NULL
                """, tid, orderId);
        for (Map<String, Object> t : shootTasks) {
            Long emp = ((Number) t.get("photographer_id")).longValue();
            Long exists = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM commission_record WHERE tenant_id=? AND order_id=? AND employee_id=? AND commission_type='SHOOT'
                    """, Long.class, tid, orderId, emp);
            if (exists == null || exists == 0) {
                List<Map<String, Object>> rules = jdbc.queryForList(
                        "SELECT * FROM commission_rule WHERE tenant_id=? AND rule_type='SHOOT' AND status=1 LIMIT 1", tid);
                BigDecimal fixed = rules.isEmpty() ? new BigDecimal("100") : (BigDecimal) rules.get(0).get("fixed_amount");
                jdbc.update("""
                        INSERT INTO commission_record(tenant_id, order_id, employee_id, commission_type, rule_snapshot,
                                                      base_amount, rate, amount, status)
                        VALUES (?,?,?, 'SHOOT', ?,?,0,?, 'PENDING')
                        """, tid, orderId, emp, "拍摄单计件 " + fixed, base, fixed);
                created++;
            }
        }
        return Map.of("created", created, "orderId", orderId);
    }

    public PageResult<Map<String, Object>> commissionDetails(String status, Long employeeId, long page, long size) {
        Long tid = TenantContext.requireTenantId();
        StringBuilder where = new StringBuilder(" WHERE c.tenant_id=?");
        List<Object> args = new ArrayList<>(List.of(tid));
        if (status != null && !status.isBlank()) {
            where.append(" AND c.status=?");
            args.add(status);
        }
        if (employeeId != null) {
            where.append(" AND c.employee_id=?");
            args.add(employeeId);
        }
        LoginContext.LoginUser user = LoginContext.get();
        if (user != null && "SELF".equals(user.getDataScope())) {
            where.append(" AND c.employee_id=(SELECT id FROM employee WHERE tenant_id=? AND user_id=? LIMIT 1)");
            args.add(tid);
            args.add(user.getId());
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM commission_record c" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add((page - 1) * size);
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT c.*, e.real_name, o.order_no, o.customer_name
                FROM commission_record c
                LEFT JOIN employee e ON e.id=c.employee_id AND e.tenant_id=c.tenant_id
                LEFT JOIN order_main o ON o.id=c.order_id AND o.tenant_id=c.tenant_id
                """ + where + " ORDER BY c.id DESC LIMIT ? OFFSET ?", pageArgs.toArray());
        return PageResult.of(total == null ? 0 : total, page, size, rows);
    }

    public Map<String, Object> performance(String from, String to) {
        Long tid = TenantContext.requireTenantId();
        if (from == null || from.isBlank()) from = LocalDate.now().withDayOfMonth(1).toString();
        if (to == null || to.isBlank()) to = LocalDate.now().toString();
        List<Map<String, Object>> byStore = jdbc.queryForList("""
                SELECT s.id, s.store_name, COUNT(o.id) AS order_count, COALESCE(SUM(o.total_amount),0) AS amount,
                       COALESCE(SUM(o.paid_amount),0) AS paid
                FROM sys_store s
                LEFT JOIN order_main o ON o.store_id=s.id AND o.tenant_id=s.tenant_id AND o.deleted=0
                     AND DATE(o.created_at) BETWEEN ? AND ?
                WHERE s.tenant_id=? AND s.deleted=0
                GROUP BY s.id, s.store_name ORDER BY amount DESC
                """, from, to, tid);
        List<Map<String, Object>> byEmployee = jdbc.queryForList("""
                SELECT e.id, e.real_name, e.role_type, COALESCE(SUM(c.amount),0) AS commission,
                       COUNT(c.id) AS item_count
                FROM employee e
                LEFT JOIN commission_record c ON c.employee_id=e.id AND c.tenant_id=e.tenant_id
                     AND DATE(c.created_at) BETWEEN ? AND ?
                WHERE e.tenant_id=? AND e.deleted=0
                GROUP BY e.id, e.real_name, e.role_type ORDER BY commission DESC
                """, from, to, tid);
        return Map.of("from", from, "to", to, "byStore", byStore, "byEmployee", byEmployee);
    }

    /** 生产端：我的今日/本周任务。 */
    public Map<String, Object> myTasks() {
        Long tid = TenantContext.requireTenantId();
        Long userId = LoginContext.require().getId();
        List<Map<String, Object>> emp = jdbc.queryForList(
                "SELECT * FROM employee WHERE tenant_id=? AND user_id=? AND deleted=0 LIMIT 1", tid, userId);
        Long employeeId = emp.isEmpty() ? null : ((Number) emp.get(0).get("id")).longValue();
        if (employeeId == null) throw new BizException(403, "当前账号未绑定员工档案");
        String sql = """
                SELECT t.*, o.order_no, o.customer_name, o.customer_phone, o.remark AS order_remark,
                       p.name AS package_name, s.store_name
                FROM shoot_task t
                LEFT JOIN order_main o ON o.id=t.order_id AND o.tenant_id=t.tenant_id
                LEFT JOIN pkg_package p ON p.id=o.package_id AND p.tenant_id=o.tenant_id
                LEFT JOIN sys_store s ON s.id=t.store_id AND s.tenant_id=t.tenant_id
                WHERE t.tenant_id=? AND (t.photographer_id=? OR t.makeup_id=? OR t.assistant_id=?)
                  AND t.shoot_date BETWEEN CURDATE() AND DATE_ADD(CURDATE(), INTERVAL 7 DAY)
                ORDER BY t.shoot_date
                """;
        return Map.of("employee", emp.get(0),
                "today", jdbc.queryForList(sql.replace("t.shoot_date BETWEEN CURDATE() AND DATE_ADD(CURDATE(), INTERVAL 7 DAY)",
                        "t.shoot_date=CURDATE()"), tid, employeeId, employeeId, employeeId),
                "week", jdbc.queryForList(sql, tid, employeeId, employeeId, employeeId));
    }
}
