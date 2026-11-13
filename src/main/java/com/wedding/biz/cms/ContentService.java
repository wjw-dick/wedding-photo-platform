package com.wedding.biz.cms;

import com.wedding.common.api.BizException;
import com.wedding.common.api.PageResult;
import com.wedding.common.context.LoginContext;
import com.wedding.common.context.TenantContext;
import com.wedding.common.util.BizNo;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/** CMS 发布、留资转线索、站内信与直播相册。 */
@Service
@RequiredArgsConstructor
public class ContentService {
    private final JdbcTemplate jdbc;
    private final BizNo bizNo;

    public PageResult<Map<String, Object>> cases(String keyword, String status, long page, long size) {
        Long tid = TenantContext.requireTenantId();
        StringBuilder where = new StringBuilder(" WHERE c.tenant_id=? AND c.deleted=0");
        List<Object> args = new ArrayList<>(List.of(tid));
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND c.title LIKE ?");
            args.add("%" + keyword + "%");
        }
        if (status != null && !status.isBlank()) {
            where.append(" AND c.status=?");
            args.add(status);
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM cms_case c" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add((page - 1) * size);
        return PageResult.of(total == null ? 0 : total, page, size,
                jdbc.queryForList("SELECT c.*, o.order_no FROM cms_case c LEFT JOIN order_main o ON o.id=c.order_id AND o.tenant_id=c.tenant_id"
                        + where + " ORDER BY c.sort_no, c.id DESC LIMIT ? OFFSET ?", pageArgs.toArray()));
    }

    @Transactional
    public void publishCase(Long id) {
        Long tid = TenantContext.requireTenantId();
        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM cms_case WHERE tenant_id=? AND id=? AND deleted=0", tid, id);
        if (((Number) row.get("authorized")).intValue() != 1) {
            throw new BizException(403, "客片未取得肖像授权，不能发布");
        }
        if (row.get("auth_revoke_at") != null) {
            throw new BizException(403, "肖像授权已撤回，不能发布");
        }
        jdbc.update("UPDATE cms_case SET status='PUBLISHED' WHERE tenant_id=? AND id=?", tid, id);
    }

    public void revokeAuth(Long id) {
        Long tid = TenantContext.requireTenantId();
        jdbc.update("""
                UPDATE cms_case SET authorized=0, auth_revoke_at=NOW(), status='OFFLINE'
                WHERE tenant_id=? AND id=?
                """, tid, id);
    }

    @Transactional
    public void publishArticle(Long id) {
        Long tid = TenantContext.requireTenantId();
        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM cms_article WHERE tenant_id=? AND id=? AND deleted=0", tid, id);
        Object publishAt = row.get("publish_at");
        String status = publishAt != null ? "SCHEDULED" : "PUBLISHED";
        if (publishAt == null) {
            jdbc.update("UPDATE cms_article SET status='PUBLISHED', publish_at=NOW() WHERE tenant_id=? AND id=?", tid, id);
        } else {
            jdbc.update("UPDATE cms_article SET status='SCHEDULED' WHERE tenant_id=? AND id=?", tid, id);
        }
    }

    public PageResult<Map<String, Object>> leadSubmits(String status, long page, long size) {
        Long tid = TenantContext.requireTenantId();
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM cms_lead_submit WHERE tenant_id=?", Long.class, tid);
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT * FROM cms_lead_submit WHERE tenant_id=? ORDER BY id DESC LIMIT ? OFFSET ?
                """, tid, size, (page - 1) * size);
        return PageResult.of(total == null ? 0 : total, page, size, rows);
    }

    @Transactional
    public Long convertSubmit(Long submitId, Long storeId, Long ownerId) {
        Long tid = TenantContext.requireTenantId();
        Map<String, Object> submit = jdbc.queryForMap(
                "SELECT * FROM cms_lead_submit WHERE tenant_id=? AND id=?", tid, submitId);
        if (submit.get("lead_id") != null) return ((Number) submit.get("lead_id")).longValue();
        org.springframework.jdbc.support.KeyHolder kh = new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbc.update(con -> {
            var ps = con.prepareStatement("""
                    INSERT INTO crm_lead(tenant_id, store_id, name, phone, source, intent, owner_id, status, protect_until)
                    VALUES (?,?,?,?, 'ONLINE', ?,?, 'NEW', DATE_ADD(NOW(), INTERVAL 7 DAY))
                    """, java.sql.Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, tid);
            ps.setObject(2, storeId);
            ps.setString(3, String.valueOf(submit.get("name")));
            ps.setString(4, String.valueOf(submit.get("phone")));
            ps.setString(5, String.valueOf(submit.get("content")));
            ps.setObject(6, ownerId);
            return ps;
        }, kh);
        Long leadId = Objects.requireNonNull(kh.getKey()).longValue();
        jdbc.update("UPDATE cms_lead_submit SET lead_id=? WHERE tenant_id=? AND id=?", leadId, tid, submitId);
        return leadId;
    }

    public List<Map<String, Object>> notices(boolean unreadOnly) {
        Long tid = TenantContext.requireTenantId();
        Long userId = LoginContext.require().getId();
        return jdbc.queryForList("""
                SELECT * FROM sys_notice WHERE tenant_id=? AND (user_id IS NULL OR user_id=?)
                """ + (unreadOnly ? " AND read_flag=0" : "") + " ORDER BY id DESC LIMIT 200", tid, userId);
    }

    public void readNotice(Long id) {
        Long tid = TenantContext.requireTenantId();
        jdbc.update("UPDATE sys_notice SET read_flag=1 WHERE tenant_id=? AND id=?", tid, id);
    }

    public Map<String, Object> testChannel(String channel, String title, String content) {
        Long tid = TenantContext.requireTenantId();
        jdbc.update("""
                INSERT INTO sys_notice(tenant_id, title, content, channel) VALUES (?,?,?,?)
                """, tid, title == null ? "消息通道测试" : title,
                content == null ? "这是一条测试消息，通道：" + channel : content, channel == null ? "SITE" : channel);
        return Map.of("ok", true, "channel", channel, "message", "测试消息已写入站内信；外部通道需配置真实凭证后投递");
    }

    // ---------------- 直播相册 ----------------

    public Map<String, Object> createAlbum(Long orderId, String name, String startAt, String endAt) {
        Long tid = TenantContext.requireTenantId();
        String token = UUID.randomUUID().toString().replace("-", "");
        jdbc.update("""
                INSERT INTO live_album(tenant_id, order_id, album_name, access_token, start_at, end_at, status)
                VALUES (?,?,?,?,?,?,1)
                """, tid, orderId, name, token, startAt, endAt);
        String tenantCode = jdbc.queryForObject("SELECT tenant_code FROM platform_tenant WHERE tenant_id=?", String.class, tid);
        return Map.of("token", token, "url", "/api/public/" + tenantCode + "/live/" + token);
    }

    public Map<String, Object> album(Long tenantId, String token) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM live_album WHERE tenant_id=? AND access_token=? AND status=1", tenantId, token);
        if (rows.isEmpty()) throw BizException.notFound("直播相册不存在");
        Map<String, Object> album = rows.get(0);
        if (album.get("end_at") != null && ((java.sql.Timestamp) album.get("end_at")).toInstant().isBefore(java.time.Instant.now())) {
            throw new BizException(410, "直播相册已过期");
        }
        Map<String, Object> result = new LinkedHashMap<>(album);
        result.put("media", jdbc.queryForList("""
                SELECT id, object_key, file_name, content_type FROM media_file
                WHERE tenant_id=? AND order_id=? AND category IN ('RAW','RETOUCHED','CHECKIN','LIVE')
                ORDER BY id DESC LIMIT 500
                """, tenantId, album.get("order_id")));
        return result;
    }
}
