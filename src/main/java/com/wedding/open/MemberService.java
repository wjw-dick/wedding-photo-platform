package com.wedding.open;

import com.wedding.common.api.BizException;
import com.wedding.common.context.LoginContext;
import com.wedding.common.context.TenantContext;
import com.wedding.common.security.JwtService;
import com.wedding.platform.entity.PlatformTenant;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MemberService {
    private final JdbcTemplate jdbc;
    private final JwtService jwtService;
    private final com.wedding.common.storage.StorageManager storageManager;

    /** UnionID 为唯一身份；手机号命中仅提示人工归并，不硬覆盖。 */
    public Map<String, Object> login(PlatformTenant tenant, String unionId, String openId, String phone,
                                     String nickname, String avatar, String source) {
        Long tid = tenant.getTenantId();
        if ((unionId == null || unionId.isBlank()) && (openId == null || openId.isBlank())) {
            throw new BizException("缺少微信身份标识");
        }
        List<Map<String, Object>> members = unionId == null || unionId.isBlank()
                ? List.of()
                : jdbc.queryForList("SELECT * FROM member WHERE tenant_id=? AND union_id=? AND deleted=0", tid, unionId);
        Long memberId;
        if (members.isEmpty()) {
            org.springframework.jdbc.support.KeyHolder kh = new org.springframework.jdbc.support.GeneratedKeyHolder();
            jdbc.update(con -> {
                var ps = con.prepareStatement("""
                        INSERT INTO member(tenant_id, union_id, open_id, phone, nickname, avatar, source, status)
                        VALUES (?,?,?,?,?,?,?,1)
                        """, java.sql.Statement.RETURN_GENERATED_KEYS);
                ps.setLong(1, tid);
                ps.setString(2, unionId);
                ps.setString(3, openId);
                ps.setString(4, phone);
                ps.setString(5, nickname);
                ps.setString(6, avatar);
                ps.setString(7, source == null ? "MINI" : source);
                return ps;
            }, kh);
            memberId = java.util.Objects.requireNonNull(kh.getKey()).longValue();
            if (phone != null && !phone.isBlank()) {
                Long phoneHits = jdbc.queryForObject("SELECT COUNT(*) FROM member WHERE tenant_id=? AND phone=? AND id<>?",
                        Long.class, tid, phone, memberId);
                if (phoneHits != null && phoneHits > 0) {
                    jdbc.update("""
                            INSERT INTO sys_notice(tenant_id, title, content, channel)
                            VALUES (?, '会员手机号待归并', ?, 'SITE')
                            """, tid, "新会员 " + memberId + " 手机号 " + phone + " 与已有会员重复，请人工确认归并");
                }
            }
        } else {
            memberId = ((Number) members.get(0).get("id")).longValue();
            jdbc.update("UPDATE member SET open_id=COALESCE(?,open_id), nickname=COALESCE(?,nickname), avatar=COALESCE(?,avatar) WHERE tenant_id=? AND id=?",
                    openId, nickname, avatar, tid, memberId);
        }
        Map<String, Object> member = jdbc.queryForMap("SELECT * FROM member WHERE tenant_id=? AND id=?", tid, memberId);
        String token = jwtService.issue(memberId, tid, tenant.getTenantCode(),
                String.valueOf(member.get("nickname")), "MEMBER", null, null);
        return Map.of("token", token, "member", member);
    }

    public Map<String, Object> profile() {
        Long tid = TenantContext.requireTenantId();
        Long id = LoginContext.require().getId();
        Map<String, Object> member = jdbc.queryForMap("SELECT * FROM member WHERE tenant_id=? AND id=?", tid, id);
        Map<String, Object> result = new LinkedHashMap<>(member);
        result.put("coupons", jdbc.queryForList("""
                SELECT g.*, c.coupon_name, c.amount, c.threshold, c.end_at
                FROM coupon_grant g JOIN coupon c ON c.id=g.coupon_id AND c.tenant_id=g.tenant_id
                WHERE g.tenant_id=? AND g.member_id=? AND g.status='UNUSED'
                """, tid, id));
        result.put("pointsLogs", jdbc.queryForList(
                "SELECT * FROM points_log WHERE tenant_id=? AND member_id=? ORDER BY id DESC LIMIT 50", tid, id));
        return result;
    }

    public List<Map<String, Object>> myOrders() {
        Long tid = TenantContext.requireTenantId();
        Long memberId = LoginContext.require().getId();
        return jdbc.queryForList("""
                SELECT o.*, p.name AS package_name, s.store_name
                FROM order_main o LEFT JOIN pkg_package p ON p.id=o.package_id AND p.tenant_id=o.tenant_id
                LEFT JOIN sys_store s ON s.id=o.store_id AND s.tenant_id=o.tenant_id
                WHERE o.tenant_id=? AND o.member_id=? AND o.deleted=0 ORDER BY o.id DESC
                """, tid, memberId);
    }

    public Map<String, Object> myOrder(Long orderId) {
        Long tid = TenantContext.requireTenantId();
        Long memberId = LoginContext.require().getId();
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT o.*, p.name AS package_name, s.store_name FROM order_main o
                LEFT JOIN pkg_package p ON p.id=o.package_id AND p.tenant_id=o.tenant_id
                LEFT JOIN sys_store s ON s.id=o.store_id AND s.tenant_id=o.tenant_id
                WHERE o.tenant_id=? AND o.member_id=? AND o.id=? AND o.deleted=0
                """, tid, memberId, orderId);
        if (rows.isEmpty()) throw BizException.forbidden("无权查看该订单");
        Map<String, Object> order = new LinkedHashMap<>(rows.get(0));
        order.put("contracts", jdbc.queryForList("SELECT * FROM order_contract_sign WHERE tenant_id=? AND order_id=?", tid, orderId));
        return order;
    }

    public List<Map<String, Object>> addresses() {
        Long tid = TenantContext.requireTenantId();
        return jdbc.queryForList("SELECT * FROM member_address WHERE tenant_id=? AND member_id=? ORDER BY is_default DESC, id DESC",
                tid, LoginContext.require().getId());
    }

    public Long saveAddress(Map<String, Object> body) {
        Long tid = TenantContext.requireTenantId();
        Long memberId = LoginContext.require().getId();
        if (body.get("id") != null) {
            jdbc.update("""
                    UPDATE member_address SET receiver=?, phone=?, province=?, city=?, district=?, detail=?, is_default=?
                    WHERE tenant_id=? AND member_id=? AND id=?
                    """, body.get("receiver"), body.get("phone"), body.get("province"), body.get("city"),
                    body.get("district"), body.get("detail"), body.getOrDefault("isDefault", 0),
                    tid, memberId, body.get("id"));
            return Long.valueOf(String.valueOf(body.get("id")));
        }
        org.springframework.jdbc.support.KeyHolder kh = new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbc.update(con -> {
            var ps = con.prepareStatement("""
                    INSERT INTO member_address(tenant_id, member_id, receiver, phone, province, city, district, detail, is_default)
                    VALUES (?,?,?,?,?,?,?,?,?)
                    """, java.sql.Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, tid);
            ps.setLong(2, memberId);
            ps.setString(3, (String) body.get("receiver"));
            ps.setString(4, (String) body.get("phone"));
            ps.setString(5, (String) body.get("province"));
            ps.setString(6, (String) body.get("city"));
            ps.setString(7, (String) body.get("district"));
            ps.setString(8, (String) body.get("detail"));
            ps.setObject(9, body.getOrDefault("isDefault", 0));
            return ps;
        }, kh);
        return java.util.Objects.requireNonNull(kh.getKey()).longValue();
    }

    public void deleteAddress(Long id) {
        jdbc.update("DELETE FROM member_address WHERE tenant_id=? AND member_id=? AND id=?",
                TenantContext.requireTenantId(), LoginContext.require().getId(), id);
    }

    public List<Map<String, Object>> myCoupons() {
        Long tid = TenantContext.requireTenantId();
        return jdbc.queryForList("""
                SELECT g.*, c.coupon_name, c.amount, c.threshold, c.start_at, c.end_at
                FROM coupon_grant g JOIN coupon c ON c.id=g.coupon_id AND c.tenant_id=g.tenant_id
                WHERE g.tenant_id=? AND g.member_id=? ORDER BY g.id DESC
                """, tid, LoginContext.require().getId());
    }

    public List<Map<String, Object>> myNotices() {
        Long tid = TenantContext.requireTenantId();
        return jdbc.queryForList("""
                SELECT * FROM sys_notice WHERE tenant_id=? ORDER BY id DESC LIMIT 100
                """, tid);
    }

    public List<Map<String, Object>> myDeliveries() {
        Long tid = TenantContext.requireTenantId();
        Long memberId = LoginContext.require().getId();
        return jdbc.queryForList("""
                SELECT d.*, o.order_no, o.customer_name FROM delivery_record d
                JOIN order_main o ON o.id=d.order_id AND o.tenant_id=d.tenant_id
                WHERE d.tenant_id=? AND o.member_id=? ORDER BY d.id DESC
                """, tid, memberId);
    }

    public Map<String, Object> myDownloads(Long orderId) {
        Long tid = TenantContext.requireTenantId();
        ensureOwner(tid, orderId);
        Long delivered = jdbc.queryForObject("""
                SELECT COUNT(*) FROM delivery_record WHERE tenant_id=? AND order_id=? AND status IN ('DONE','NOTIFIED')
                """, Long.class, tid, orderId);
        if (delivered == null || delivered == 0) throw BizException.forbidden("客片尚未交付");
        java.util.List<Map<String, Object>> files = jdbc.queryForList("""
                SELECT id, file_name, object_key, provider, content_type, size_bytes, category
                FROM media_file WHERE tenant_id=? AND order_id=? AND category IN ('RETOUCHED','PRINT') ORDER BY id DESC
                """, tid, orderId);
        for (Map<String, Object> f : files) {
            String provider = f.get("provider") == null ? storageManager.activeConfig(tid).getProvider() : String.valueOf(f.get("provider"));
            f.put("url", storageManager.signedUrl(tid, provider, String.valueOf(f.get("object_key")), 30));
        }
        return Map.of("files", files, "watermark", "婚礼影像·预览水印");
    }

    private void ensureOwner(Long tid, Long orderId) {
        Long owner = jdbc.queryForObject("SELECT member_id FROM order_main WHERE tenant_id=? AND id=?", Long.class, tid, orderId);
        if (owner == null || !owner.equals(LoginContext.require().getId())) throw BizException.forbidden("无权访问该订单");
    }
}
