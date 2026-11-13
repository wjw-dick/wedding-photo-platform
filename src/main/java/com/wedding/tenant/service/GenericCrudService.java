package com.wedding.tenant.service;

import com.wedding.common.api.BizException;
import com.wedding.common.api.PageResult;
import com.wedding.common.audit.AuditService;
import com.wedding.common.context.LoginContext;
import com.wedding.common.context.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 标准业务表的通用 CRUD 通道：表/列双白名单 + 强制租户条件 + 写审计。
 * 订单状态机、档期锁、支付等强流程走专用接口，不允许从这里改状态。
 */
@Service
@RequiredArgsConstructor
public class GenericCrudService {
    private static final Map<String, String> MODULE = Map.ofEntries(
            Map.entry("sys_store", "store"), Map.entry("store_studio", "store"),
            Map.entry("pkg_package", "package"), Map.entry("pkg_package_item", "package"),
            Map.entry("pkg_package_store", "package"), Map.entry("pkg_addon", "package"),
            Map.entry("pkg_addon_price", "package"), Map.entry("sch_resource", "schedule"),
            Map.entry("order_item", "order"), Map.entry("order_contract", "order"),
            Map.entry("shoot_task", "production"), Map.entry("shoot_checkin", "production"),
            Map.entry("media_file", "production"), Map.entry("upload_task", "production"),
            Map.entry("selection_sheet", "selection"), Map.entry("selection_item", "selection"),
            Map.entry("retouch_task", "retouch"), Map.entry("product_order", "delivery"),
            Map.entry("delivery_record", "delivery"), Map.entry("dress", "dress"),
            Map.entry("dress_rental", "dress"), Map.entry("member", "member"),
            Map.entry("member_account", "member"), Map.entry("member_level", "member"),
            Map.entry("points_log", "member"), Map.entry("coupon", "coupon"),
            Map.entry("coupon_grant", "coupon"), Map.entry("crm_lead", "lead"),
            Map.entry("lead_follow", "lead"), Map.entry("channel", "channel"),
            Map.entry("channel_report", "channel"), Map.entry("commission_record", "channel"),
            Map.entry("settlement", "channel"), Map.entry("supplier", "supplier"),
            Map.entry("supplier_qualification", "supplier"), Map.entry("employee", "employee"),
            Map.entry("employee_schedule", "employee"), Map.entry("commission_rule", "employee"),
            Map.entry("commission_detail", "employee"), Map.entry("report_task", "report"),
            Map.entry("qr_entry", "entry"), Map.entry("cms_banner", "cms"),
            Map.entry("cms_article", "cms"), Map.entry("cms_case", "cms"),
            Map.entry("cms_route", "cms"), Map.entry("cms_lead_form", "cms"),
            Map.entry("cms_lead_submit", "lead"), Map.entry("mp_fan", "mp"),
            Map.entry("mp_tag", "mp"), Map.entry("mp_message", "mp"),
            Map.entry("mp_material", "mp"), Map.entry("mp_draft", "mp"),
            Map.entry("mp_publish", "mp"), Map.entry("mp_auto_reply", "mp"),
            Map.entry("mp_template", "mp"), Map.entry("mp_menu_publish", "mp"),
            Map.entry("msg_template", "system"), Map.entry("msg_channel", "system"),
            Map.entry("sms_channel", "system"), Map.entry("sys_language", "system"),
            Map.entry("sys_i18n", "system"), Map.entry("sys_notice", "system"),
            Map.entry("sys_param", "system")
    );

    private final JdbcTemplate jdbc;
    private final AuditService auditService;
    private final Map<String, List<String>> columnCache = new HashMap<>();

    public void checkPermission(String table, boolean write) {
        String module = MODULE.get(table);
        if (module == null) {
            throw new BizException(403, "该资源不允许操作");
        }
        String perm = module + (write ? ":edit" : ":view");
        if (!LoginContext.require().hasPermission(perm)) {
            throw BizException.forbidden("无权限：" + perm);
        }
    }

    public PageResult<Map<String, Object>> list(String table, Map<String, String> params, long page, long size) {
        checkPermission(table, false);
        List<String> columns = columns(table);
        Long tenantId = TenantContext.requireTenantId();
        StringBuilder where = new StringBuilder(" WHERE tenant_id=?");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        String keyword = params.get("keyword");
        if (keyword != null && !keyword.isBlank()) {
            List<String> searchable = columns.stream()
                    .filter(c -> c.endsWith("_name") || c.equals("name") || c.equals("title")
                            || c.equals("phone") || c.equals("order_no") || c.equals("no"))
                    .toList();
            if (!searchable.isEmpty()) {
                where.append(" AND (").append(String.join(" OR ", searchable.stream().map(c -> c + " LIKE ?").toList())).append(")");
                for (int i = 0; i < searchable.size(); i++) args.add("%" + keyword + "%");
            }
        }
        for (Map.Entry<String, String> e : params.entrySet()) {
            String key = e.getKey();
            if (List.of("page", "size", "keyword").contains(key)) continue;
            if (columns.contains(key) && e.getValue() != null && !e.getValue().isBlank()) {
                where.append(" AND ").append(key).append("=?");
                args.add(e.getValue());
            }
        }
        LoginContext.LoginUser user = LoginContext.get();
        if (user != null && "STORE".equals(user.getDataScope()) && columns.contains("store_id")
                && user.getStoreId() != null && user.getStoreId() > 0) {
            where.append(" AND store_id=?");
            args.add(user.getStoreId());
        }
        String orderBy = columns.contains("id") ? " ORDER BY id DESC" : "";
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add((page - 1) * size);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM " + table + where + orderBy + " LIMIT ? OFFSET ?", pageArgs.toArray());
        return PageResult.of(total == null ? 0 : total, page, size, rows);
    }

    public Map<String, Object> get(String table, Long id) {
        checkPermission(table, false);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM " + table + " WHERE tenant_id=? AND id=?", TenantContext.requireTenantId(), id);
        if (rows.isEmpty()) throw BizException.notFound("记录不存在");
        return rows.get(0);
    }

    public Long create(String table, Map<String, Object> body) {
        checkPermission(table, true);
        List<String> columns = columns(table);
        Map<String, Object> data = new LinkedHashMap<>();
        for (String c : columns) {
            if (List.of("id", "tenant_id", "created_at", "updated_at", "deleted").contains(c)) continue;
            if (body.containsKey(c)) data.put(c, body.get(c));
        }
        data.put("tenant_id", TenantContext.requireTenantId());
        String cols = String.join(",", data.keySet());
        String marks = String.join(",", Collections.nCopies(data.size(), "?"));
        org.springframework.jdbc.support.KeyHolder kh = new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbc.update(con -> {
            var ps = con.prepareStatement("INSERT INTO " + table + "(" + cols + ") VALUES (" + marks + ")",
                    java.sql.Statement.RETURN_GENERATED_KEYS);
            int i = 1;
            for (Object v : data.values()) ps.setObject(i++, v);
            return ps;
        }, kh);
        Long id = Objects.requireNonNull(kh.getKey()).longValue();
        auditService.record(module(table), "create", table, String.valueOf(id),
                "新增 " + table + " id=" + id, "SUCCESS", 0);
        return id;
    }

    public void update(String table, Long id, Map<String, Object> body) {
        checkPermission(table, true);
        List<String> columns = columns(table);
        Map<String, Object> data = new LinkedHashMap<>();
        for (String c : columns) {
            if (List.of("id", "tenant_id", "created_at", "updated_at", "deleted").contains(c)) continue;
            if (body.containsKey(c)) data.put(c, body.get(c));
        }
        if (data.isEmpty()) return;
        String sets = String.join(",", data.keySet().stream().map(c -> c + "=?").toList());
        List<Object> args = new ArrayList<>(data.values());
        args.add(TenantContext.requireTenantId());
        args.add(id);
        jdbc.update("UPDATE " + table + " SET " + sets + " WHERE tenant_id=? AND id=?", args.toArray());
        auditService.record(module(table), "update", table, String.valueOf(id),
                "修改 " + table + " id=" + id, "SUCCESS", 0);
    }

    public void delete(String table, Long id) {
        checkPermission(table, true);
        List<String> columns = columns(table);
        if (columns.contains("deleted")) {
            jdbc.update("UPDATE " + table + " SET deleted=1 WHERE tenant_id=? AND id=?",
                    TenantContext.requireTenantId(), id);
        } else {
            jdbc.update("DELETE FROM " + table + " WHERE tenant_id=? AND id=?",
                    TenantContext.requireTenantId(), id);
        }
        auditService.record(module(table), "delete", table, String.valueOf(id),
                "删除 " + table + " id=" + id, "SUCCESS", 0);
    }

    private String module(String table) {
        return MODULE.getOrDefault(table, "biz");
    }

    private synchronized List<String> columns(String table) {
        return columnCache.computeIfAbsent(table, t -> {
            if (!MODULE.containsKey(t)) throw new BizException(403, "非法表名");
            List<String> cols = jdbc.queryForList("""
                    SELECT COLUMN_NAME FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=?
                    """, String.class, t);
            if (cols.isEmpty()) throw new BizException("数据表不存在");
            return cols;
        });
    }
}
