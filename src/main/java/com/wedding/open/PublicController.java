package com.wedding.open;

import com.wedding.common.api.R;
import com.wedding.platform.entity.PlatformTenant;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/public/{tenantCode}")
@RequiredArgsConstructor
public class PublicController {
    private final TenantResolver tenantResolver;
    private final JdbcTemplate jdbc;
    private final com.wedding.biz.cms.ContentService contentService;

    @GetMapping("/home")
    public R<Map<String, Object>> home(@PathVariable String tenantCode) {
        Long tid = tenantResolver.byCode(tenantCode).getTenantId();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("banners", jdbc.queryForList("""
                SELECT * FROM cms_banner WHERE tenant_id=? AND status=1
                  AND (start_at IS NULL OR start_at<=NOW()) AND (end_at IS NULL OR end_at>=NOW())
                ORDER BY sort_no, id DESC
                """, tid));
        m.put("packages", packages(tid));
        m.put("cases", jdbc.queryForList("""
                SELECT id, title, cover_url, lang_code FROM cms_case
                WHERE tenant_id=? AND status='PUBLISHED' AND authorized=1 AND auth_revoke_at IS NULL AND deleted=0
                ORDER BY sort_no, id DESC LIMIT 12
                """, tid));
        m.put("routes", jdbc.queryForList(
                "SELECT * FROM cms_route WHERE tenant_id=? AND status=1 ORDER BY id DESC LIMIT 8", tid));
        m.put("articles", jdbc.queryForList("""
                SELECT id, title, summary, cover_url, publish_at FROM cms_article
                WHERE tenant_id=? AND status='PUBLISHED' AND deleted=0 ORDER BY publish_at DESC LIMIT 6
                """, tid));
        m.put("stores", jdbc.queryForList(
                "SELECT id, store_name, address, phone, business_hours, longitude, latitude FROM sys_store WHERE tenant_id=? AND status=1 AND deleted=0", tid));
        m.put("params", jdbc.query("SELECT param_key, param_value FROM sys_param WHERE tenant_id=?",
                rs -> {
                    Map<String, String> map = new LinkedHashMap<>();
                    while (rs.next()) map.put(rs.getString(1), rs.getString(2));
                    return map;
                }, tid));
        return R.ok(m);
    }

    @GetMapping("/packages")
    public R<List<Map<String, Object>>> packagesList(@PathVariable String tenantCode,
                                                     @RequestParam(required = false) Long storeId,
                                                     @RequestParam(required = false) String style,
                                                     @RequestParam(required = false) String keyword) {
        Long tid = tenantResolver.byCode(tenantCode).getTenantId();
        List<Map<String, Object>> list = packages(tid);
        if (keyword != null && !keyword.isBlank()) {
            list = list.stream().filter(p -> String.valueOf(p.get("name")).contains(keyword)).toList();
        }
        return R.ok(list);
    }

    @GetMapping("/packages/{id}")
    public R<Map<String, Object>> packageDetail(@PathVariable String tenantCode, @PathVariable Long id) {
        Long tid = tenantResolver.byCode(tenantCode).getTenantId();
        Map<String, Object> pkg = jdbc.queryForMap(
                "SELECT * FROM pkg_package WHERE tenant_id=? AND id=? AND status='ON' AND deleted=0", tid, id);
        Map<String, Object> result = new LinkedHashMap<>(pkg);
        result.put("items", jdbc.queryForList(
                "SELECT item_name, item_value FROM pkg_package_item WHERE tenant_id=? AND package_id=? ORDER BY sort_no", tid, id));
        result.put("addons", jdbc.queryForList(
                "SELECT id, addon_name, addon_type, unit, base_price FROM pkg_addon WHERE tenant_id=? AND status=1 AND deleted=0", tid));
        return R.ok(result);
    }

    @GetMapping("/cases")
    public R<List<Map<String, Object>>> cases(@PathVariable String tenantCode) {
        Long tid = tenantResolver.byCode(tenantCode).getTenantId();
        return R.ok(jdbc.queryForList("""
                SELECT * FROM cms_case WHERE tenant_id=? AND status='PUBLISHED' AND authorized=1 AND auth_revoke_at IS NULL AND deleted=0
                ORDER BY sort_no, id DESC
                """, tid));
    }

    @GetMapping("/articles")
    public R<List<Map<String, Object>>> articles(@PathVariable String tenantCode) {
        Long tid = tenantResolver.byCode(tenantCode).getTenantId();
        return R.ok(jdbc.queryForList("""
                SELECT id, title, summary, cover_url, publish_at FROM cms_article
                WHERE tenant_id=? AND status='PUBLISHED' AND deleted=0 ORDER BY publish_at DESC
                """, tid));
    }

    @GetMapping("/articles/{id}")
    public R<Map<String, Object>> article(@PathVariable String tenantCode, @PathVariable Long id) {
        Long tid = tenantResolver.byCode(tenantCode).getTenantId();
        return R.ok(jdbc.queryForMap(
                "SELECT * FROM cms_article WHERE tenant_id=? AND id=? AND status='PUBLISHED' AND deleted=0", tid, id));
    }

    @GetMapping("/routes")
    public R<List<Map<String, Object>>> routes(@PathVariable String tenantCode) {
        Long tid = tenantResolver.byCode(tenantCode).getTenantId();
        return R.ok(jdbc.queryForList("SELECT * FROM cms_route WHERE tenant_id=? AND status=1", tid));
    }

    @GetMapping("/stores")
    public R<List<Map<String, Object>>> stores(@PathVariable String tenantCode) {
        Long tid = tenantResolver.byCode(tenantCode).getTenantId();
        return R.ok(jdbc.queryForList(
                "SELECT id, store_name, address, phone, business_hours, longitude, latitude FROM sys_store WHERE tenant_id=? AND status=1 AND deleted=0", tid));
    }

    @GetMapping("/menu/{endType}")
    public R<List<Map<String, Object>>> menu(@PathVariable String tenantCode, @PathVariable String endType) {
        Long tid = tenantResolver.byCode(tenantCode).getTenantId();
        List<Map<String, Object>> configs = jdbc.queryForList(
                "SELECT id FROM sys_menu_config WHERE tenant_id=? AND end_type=?", tid, endType);
        if (configs.isEmpty()) return R.ok(List.of());
        return R.ok(jdbc.queryForList("""
                SELECT id, parent_id, title, icon, item_type, path, sort_no, visible, extra_json
                FROM sys_menu_item WHERE tenant_id=? AND config_id=? ORDER BY sort_no, id
                """, tid, configs.get(0).get("id")));
    }

    @PostMapping("/leads")
    public R<Map<String, Object>> lead(@PathVariable String tenantCode, @RequestBody Map<String, Object> body,
                                       jakarta.servlet.http.HttpServletRequest request) {
        Long tid = tenantResolver.byCode(tenantCode).getTenantId();
        String phone = String.valueOf(body.getOrDefault("phone", ""));
        if (!phone.matches("^1\\d{10}$")) {
            return R.fail(400, "请输入正确手机号");
        }
        Long recent = jdbc.queryForObject("""
                SELECT COUNT(*) FROM cms_lead_submit WHERE tenant_id=? AND phone=? AND created_at>DATE_SUB(NOW(), INTERVAL 1 MINUTE)
                """, Long.class, tid, phone);
        if (recent != null && recent > 0) {
            return R.fail(429, "请勿重复提交，稍后再试");
        }
        Long storeId = body.get("storeId") == null ? null : Long.valueOf(String.valueOf(body.get("storeId")));
        org.springframework.jdbc.support.KeyHolder kh = new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbc.update(con -> {
            var ps = con.prepareStatement("""
                    INSERT INTO crm_lead(tenant_id, store_id, name, phone, source, intent, status, protect_until)
                    VALUES (?,?,?,?, 'ONLINE', ?, 'NEW', DATE_ADD(NOW(), INTERVAL 7 DAY))
                    """, java.sql.Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, tid);
            ps.setObject(2, storeId);
            ps.setString(3, String.valueOf(body.getOrDefault("name", "")));
            ps.setString(4, phone);
            ps.setString(5, String.valueOf(body.getOrDefault("intent", "")));
            return ps;
        }, kh);
        Long leadId = java.util.Objects.requireNonNull(kh.getKey()).longValue();
        jdbc.update("""
                INSERT INTO cms_lead_submit(tenant_id, name, phone, content, lead_id, ip)
                VALUES (?,?,?,?,?,?)
                """, tid, body.get("name"), phone, body.get("content"), leadId, request.getRemoteAddr());
        return R.ok(Map.of("leadId", leadId, "message", "提交成功，顾问将在 30 分钟内联系您"));
    }

    @GetMapping("/live/{token}")
    public R<Map<String, Object>> live(@PathVariable String tenantCode, @PathVariable String token) {
        Long tid = tenantResolver.byCode(tenantCode).getTenantId();
        return R.ok(contentService.album(tid, token));
    }

    private List<Map<String, Object>> packages(Long tid) {
        return jdbc.queryForList("""
                SELECT id, package_no, name, name_en, cover_url, shoot_duration, dress_count, scene_count,
                       finished_count, retouch_count, album_spec, price, deposit, delivery_days, status
                FROM pkg_package WHERE tenant_id=? AND status='ON' AND deleted=0 ORDER BY sort_no, id DESC
                """, tid);
    }
}
