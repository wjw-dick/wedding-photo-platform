package com.wedding.biz.crm;

import com.wedding.common.api.BizException;
import com.wedding.common.api.PageResult;
import com.wedding.common.context.LoginContext;
import com.wedding.common.context.TenantContext;
import com.wedding.common.security.TenantDataGuard;
import com.wedding.common.util.BizNo;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/** 会员、线索 CRM、优惠券与积分/储值。 */
@Service
@RequiredArgsConstructor
public class CrmService {
    private final JdbcTemplate jdbc;
    private final TenantDataGuard tenantDataGuard;
    private final BizNo bizNo;

    // ---------------- 线索 ----------------

    public PageResult<Map<String, Object>> leads(String keyword, String status, Long storeId, Long ownerId,
                                                 long page, long size) {
        Long tid = TenantContext.requireTenantId();
        StringBuilder where = new StringBuilder(" WHERE l.tenant_id=? AND l.deleted=0");
        List<Object> args = new ArrayList<>();
        args.add(tid);
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (l.name LIKE ? OR l.phone LIKE ?)");
            args.add("%" + keyword + "%");
            args.add("%" + keyword + "%");
        }
        if (status != null && !status.isBlank()) {
            where.append(" AND l.status=?");
            args.add(status);
        }
        if (storeId != null) {
            where.append(" AND l.store_id=?");
            args.add(storeId);
        }
        if (ownerId != null) {
            where.append(" AND l.owner_id=?");
            args.add(ownerId);
        }
        LoginContext.LoginUser user = LoginContext.get();
        if (user != null && "STORE".equals(user.getDataScope()) && user.getStoreId() != null && user.getStoreId() > 0) {
            where.append(" AND l.store_id=?");
            args.add(user.getStoreId());
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM crm_lead l" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add((page - 1) * size);
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT l.*, s.store_name, u.real_name AS owner_name, c.channel_name
                FROM crm_lead l
                LEFT JOIN sys_store s ON s.id=l.store_id AND s.tenant_id=l.tenant_id
                LEFT JOIN sys_user u ON u.id=l.owner_id AND u.tenant_id=l.tenant_id
                LEFT JOIN channel c ON c.id=l.channel_id AND c.tenant_id=l.tenant_id
                """ + where + " ORDER BY l.id DESC LIMIT ? OFFSET ?", pageArgs.toArray());
        return PageResult.of(total == null ? 0 : total, page, size, rows);
    }

    @Transactional
    public Long createLead(Map<String, Object> body) {
        Long tid = TenantContext.requireTenantId();
        String phone = String.valueOf(body.getOrDefault("phone", ""));
        if (!phone.matches("^1\\d{10}$")) throw new BizException("请输入正确手机号");
        Long dup = jdbc.queryForObject("""
                SELECT COUNT(*) FROM crm_lead WHERE tenant_id=? AND phone=? AND deleted=0
                  AND (status NOT IN ('INVALID','DEAL') OR protect_until > NOW())
                """, Long.class, tid, phone);
        if (dup != null && dup > 0) throw new BizException(409, "该手机号已有保护期内的线索，请直接跟进");
        int protectDays = 7;
        try {
            Integer d = jdbc.queryForObject("SELECT CAST(param_value AS UNSIGNED) FROM sys_param WHERE tenant_id=? AND param_key='lead.protect.days'",
                    Integer.class, tid);
            if (d != null) protectDays = d;
        } catch (Exception ignored) {
        }
        final int protect = protectDays;
        org.springframework.jdbc.support.KeyHolder kh = new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbc.update(con -> {
            var ps = con.prepareStatement("""
                    INSERT INTO crm_lead(tenant_id, store_id, name, phone, source, channel_id, package_id, intent,
                                         owner_id, protect_until, status)
                    VALUES (?,?,?,?,?,?,?,?,?,?, 'NEW')
                    """, java.sql.Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, tid);
            ps.setObject(2, body.get("storeId"));
            ps.setString(3, String.valueOf(body.getOrDefault("name", "")));
            ps.setString(4, phone);
            ps.setString(5, String.valueOf(body.getOrDefault("source", "STORE")));
            ps.setObject(6, body.get("channelId"));
            ps.setObject(7, body.get("packageId"));
            ps.setString(8, String.valueOf(body.getOrDefault("intent", "")));
            ps.setObject(9, body.get("ownerId") == null ? LoginContext.require().getId() : body.get("ownerId"));
            ps.setObject(10, LocalDateTime.now().plusDays(protect));
            return ps;
        }, kh);
        return Objects.requireNonNull(kh.getKey()).longValue();
    }

    @Transactional
    public void follow(Long leadId, String followType, String content, String nextFollowAt) {
        Long tid = TenantContext.requireTenantId();
        Map<String, Object> lead = jdbc.queryForMap("SELECT * FROM crm_lead WHERE tenant_id=? AND id=? AND deleted=0", tid, leadId);
        jdbc.update("""
                INSERT INTO lead_follow(tenant_id, lead_id, follow_type, content, operator_id, next_follow_at)
                VALUES (?,?,?,?,?,?)
                """, tid, leadId, followType, content, LoginContext.require().getId(), nextFollowAt);
        if ("NEW".equals(lead.get("status"))) {
            jdbc.update("UPDATE crm_lead SET status='FOLLOWING' WHERE tenant_id=? AND id=?", tid, leadId);
        }
    }

    public Map<String, Object> leadDetail(Long leadId) {
        Long tid = TenantContext.requireTenantId();
        Map<String, Object> lead;
        try {
            lead = jdbc.queryForMap("SELECT * FROM crm_lead WHERE tenant_id=? AND id=? AND deleted=0", tid, leadId);
        } catch (Exception e) {
            throw BizException.notFound("线索不存在");
        }
        Map<String, Object> result = new LinkedHashMap<>(lead);
        result.put("follows", jdbc.queryForList(
                "SELECT * FROM lead_follow WHERE tenant_id=? AND lead_id=? ORDER BY id DESC", tid, leadId));
        return result;
    }

    @Transactional
    public void assignLead(Long leadId, Long ownerId, Long storeId, String status) {
        Long tid = TenantContext.requireTenantId();
        jdbc.update("""
                UPDATE crm_lead SET owner_id=COALESCE(?,owner_id), store_id=COALESCE(?,store_id),
                                    status=COALESCE(?,status)
                WHERE tenant_id=? AND id=? AND deleted=0
                """, ownerId, storeId, status, tid, leadId);
    }

    public List<Map<String, Object>> leadReminders() {
        Long tid = TenantContext.requireTenantId();
        return jdbc.queryForList("""
                SELECT l.*, DATEDIFF(l.protect_until, NOW()) AS protect_days
                FROM crm_lead l
                WHERE l.tenant_id=? AND l.deleted=0 AND l.status IN ('NEW','FOLLOWING')
                  AND (l.protect_until IS NULL OR l.protect_until <= DATE_ADD(NOW(), INTERVAL 2 DAY))
                ORDER BY l.protect_until
                """, tid);
    }

    /** 线索转订单：写入订单主表并把线索标记成交。 */
    @Transactional
    public Long convertLead(Long leadId, Long packageId, BigDecimal amount) {
        Long tid = TenantContext.requireTenantId();
        Map<String, Object> lead = jdbc.queryForMap("SELECT * FROM crm_lead WHERE tenant_id=? AND id=?", tid, leadId);
        String orderNo = bizNo.orderNo();
        jdbc.update("""
                INSERT INTO order_main(tenant_id, order_no, store_id, customer_name, customer_phone, package_id,
                    channel_id, salesman_id, status, total_amount, deposit_amount, balance_amount, source, remark)
                VALUES (?,?,?,?,?,?,?,?, 'LEAD', ?,0,?, 'STORE', ?)
                """, tid, orderNo, lead.get("store_id"), lead.get("name"), lead.get("phone"), packageId,
                lead.get("channel_id"), lead.get("owner_id"), amount, amount, "线索转订单");
        Long orderId = jdbc.queryForObject("SELECT id FROM order_main WHERE tenant_id=? AND order_no=?", Long.class, tid, orderNo);
        jdbc.update("UPDATE crm_lead SET status='DEAL' WHERE tenant_id=? AND id=?", tid, leadId);
        return orderId;
    }

    // ---------------- 会员 ----------------

    public PageResult<Map<String, Object>> members(String keyword, Long levelId, long page, long size) {
        Long tid = TenantContext.requireTenantId();
        StringBuilder where = new StringBuilder(" WHERE m.tenant_id=? AND m.deleted=0");
        List<Object> args = new ArrayList<>();
        args.add(tid);
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (m.nickname LIKE ? OR m.phone LIKE ? OR m.real_name LIKE ?)");
            for (int i = 0; i < 3; i++) args.add("%" + keyword + "%");
        }
        if (levelId != null) {
            where.append(" AND m.level_id=?");
            args.add(levelId);
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM member m" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add((page - 1) * size);
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT m.*, l.level_name FROM member m
                LEFT JOIN member_level l ON l.id=m.level_id AND l.tenant_id=m.tenant_id
                """ + where + " ORDER BY m.id DESC LIMIT ? OFFSET ?", pageArgs.toArray());
        return PageResult.of(total == null ? 0 : total, page, size, rows);
    }

    @Transactional
    public void adjustPoints(Long memberId, int points, String reason) {
        Long tid = TenantContext.requireTenantId();
        Map<String, Object> member = jdbc.queryForMap("SELECT * FROM member WHERE tenant_id=? AND id=?", tid, memberId);
        int after = ((Number) member.get("points")).intValue() + points;
        if (after < 0) throw new BizException("积分不足");
        jdbc.update("UPDATE member SET points=? WHERE tenant_id=? AND id=?", after, tid, memberId);
        jdbc.update("""
                INSERT INTO points_log(tenant_id, member_id, points, biz_type, biz_no)
                VALUES (?,?,?,?,?)
                """, tid, memberId, points, reason, bizNo.next("PT"));
    }

    @Transactional
    public void adjustBalance(Long memberId, BigDecimal amount, String changeType, String remark) {
        Long tid = TenantContext.requireTenantId();
        Map<String, Object> member = jdbc.queryForMap("SELECT * FROM member WHERE tenant_id=? AND id=?", tid, memberId);
        BigDecimal after = ((BigDecimal) member.get("balance")).add(amount);
        if (after.compareTo(BigDecimal.ZERO) < 0) throw new BizException("余额不足");
        jdbc.update("UPDATE member SET balance=? WHERE tenant_id=? AND id=?", after, tid, memberId);
        jdbc.update("""
                INSERT INTO member_account(tenant_id, member_id, change_type, amount, balance_after, biz_no, remark)
                VALUES (?,?,?,?,?,?,?)
                """, tid, memberId, changeType, amount, after, bizNo.next("MB"), remark);
    }

    public Map<String, Object> memberDetail(Long memberId) {
        Long tid = TenantContext.requireTenantId();
        tenantDataGuard.requireOwned("member", memberId, "会员");
        Map<String, Object> result = new LinkedHashMap<>(jdbc.queryForMap(
                "SELECT * FROM member WHERE tenant_id=? AND id=?", tid, memberId));
        result.put("pointsLogs", jdbc.queryForList(
                "SELECT * FROM points_log WHERE tenant_id=? AND member_id=? ORDER BY id DESC LIMIT 100", tid, memberId));
        result.put("accounts", jdbc.queryForList(
                "SELECT * FROM member_account WHERE tenant_id=? AND member_id=? ORDER BY id DESC LIMIT 100", tid, memberId));
        result.put("coupons", jdbc.queryForList("""
                SELECT g.*, c.coupon_name, c.amount, c.threshold FROM coupon_grant g
                JOIN coupon c ON c.id=g.coupon_id AND c.tenant_id=g.tenant_id
                WHERE g.tenant_id=? AND g.member_id=? ORDER BY g.id DESC
                """, tid, memberId));
        result.put("orders", jdbc.queryForList(
                "SELECT * FROM order_main WHERE tenant_id=? AND member_id=? AND deleted=0 ORDER BY id DESC", tid, memberId));
        return result;
    }

    // ---------------- 优惠券 ----------------

    @Transactional
    public Map<String, Object> grantCoupon(Long couponId, Long memberId, int count) {
        Long tid = TenantContext.requireTenantId();
        Map<String, Object> coupon = jdbc.queryForMap("SELECT * FROM coupon WHERE tenant_id=? AND id=?", tid, couponId);
        int issued = ((Number) coupon.get("issued_count")).intValue();
        int total = ((Number) coupon.get("total_count")).intValue();
        if (total > 0 && issued + count > total) throw new BizException("优惠券库存不足");
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            org.springframework.jdbc.support.KeyHolder kh = new org.springframework.jdbc.support.GeneratedKeyHolder();
            jdbc.update(con -> {
                var ps = con.prepareStatement("""
                        INSERT INTO coupon_grant(tenant_id, coupon_id, member_id, status) VALUES (?,?,?, 'UNUSED')
                        """, java.sql.Statement.RETURN_GENERATED_KEYS);
                ps.setLong(1, tid);
                ps.setLong(2, couponId);
                ps.setObject(3, memberId);
                return ps;
            }, kh);
            ids.add(Objects.requireNonNull(kh.getKey()).longValue());
        }
        jdbc.update("UPDATE coupon SET issued_count=issued_count+? WHERE tenant_id=? AND id=?", count, tid, couponId);
        return Map.of("granted", count, "grantIds", ids);
    }

    @Transactional
    public Map<String, Object> redeemCoupon(Long grantId, Long orderId) {
        Long tid = TenantContext.requireTenantId();
        Map<String, Object> grant = jdbc.queryForMap("SELECT * FROM coupon_grant WHERE tenant_id=? AND id=? FOR UPDATE", tid, grantId);
        if (!"UNUSED".equals(grant.get("status"))) throw new BizException("优惠券已使用或已失效");
        Map<String, Object> coupon = jdbc.queryForMap("SELECT * FROM coupon WHERE tenant_id=? AND id=?", tid, grant.get("coupon_id"));
        if (coupon.get("end_at") != null && ((java.sql.Timestamp) coupon.get("end_at")).toLocalDateTime().isBefore(LocalDateTime.now())) {
            throw new BizException("优惠券已过期");
        }
        Map<String, Object> order = jdbc.queryForMap("SELECT * FROM order_main WHERE tenant_id=? AND id=?", tid, orderId);
        BigDecimal total = (BigDecimal) order.get("total_amount");
        BigDecimal threshold = (BigDecimal) coupon.get("threshold");
        if (total.compareTo(threshold) < 0) throw new BizException("订单金额未达到优惠券使用门槛");
        BigDecimal amount = (BigDecimal) coupon.get("amount");
        jdbc.update("UPDATE coupon_grant SET status='USED', used_at=NOW(), order_id=? WHERE tenant_id=? AND id=?",
                orderId, tid, grantId);
        jdbc.update("UPDATE order_main SET total_amount=GREATEST(total_amount-?,0), balance_amount=GREATEST(balance_amount-?,0) WHERE tenant_id=? AND id=?",
                amount, amount, tid, orderId);
        return Map.of("discount", amount, "orderId", orderId);
    }
}
