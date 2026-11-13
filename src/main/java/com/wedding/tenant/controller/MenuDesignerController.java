package com.wedding.tenant.controller;

import com.wedding.common.api.BizException;
import com.wedding.common.api.R;
import com.wedding.common.audit.Audited;
import com.wedding.common.context.TenantContext;
import com.wedding.common.security.RequirePermission;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.*;

@RestController
@RequestMapping("/api/tenant/menu-designer")
@RequiredArgsConstructor
public class MenuDesignerController {
    private final JdbcTemplate jdbc;

    @GetMapping
    @RequirePermission("system:view")
    public R<List<Map<String, Object>>> configs() {
        return R.ok(jdbc.queryForList("""
                SELECT c.*, (SELECT COUNT(*) FROM sys_menu_item i WHERE i.config_id=c.id AND i.tenant_id=c.tenant_id) AS item_count
                FROM sys_menu_config c WHERE c.tenant_id=? ORDER BY c.id
                """, TenantContext.requireTenantId()));
    }

    @GetMapping("/{configId}/items")
    @RequirePermission("system:view")
    public R<List<Map<String, Object>>> items(@PathVariable Long configId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, parent_id, title, icon, item_type, path, sort_no, visible, extra_json
                FROM sys_menu_item WHERE tenant_id=? AND config_id=? ORDER BY sort_no, id
                """, TenantContext.requireTenantId(), configId);
        return R.ok(tree(rows, 0L));
    }

    @PutMapping("/{configId}/items")
    @RequirePermission("system:edit")
    @Audited(module = "menuDesigner", action = "save", targetType = "menuConfig")
    @Transactional
    public R<Void> save(@PathVariable Long configId, @RequestBody List<Map<String, Object>> items) {
        Long tid = TenantContext.requireTenantId();
        Map<String, Object> cfg = jdbc.queryForMap(
                "SELECT * FROM sys_menu_config WHERE tenant_id=? AND id=?", tid, configId);
        validate(String.valueOf(cfg.get("end_type")), items);
        jdbc.update("DELETE FROM sys_menu_item WHERE tenant_id=? AND config_id=?", tid, configId);
        insertItems(tid, configId, 0L, items);
        jdbc.update("UPDATE sys_menu_config SET published=1 WHERE tenant_id=? AND id=?", tid, configId);
        return R.ok();
    }

    @PostMapping("/{configId}/reset")
    @RequirePermission("system:edit")
    @Audited(module = "menuDesigner", action = "reset", targetType = "menuConfig")
    public R<Void> reset(@PathVariable Long configId) {
        Long tid = TenantContext.requireTenantId();
        jdbc.update("DELETE FROM sys_menu_item WHERE tenant_id=? AND config_id=?", tid, configId);
        jdbc.update("INSERT INTO sys_menu_item(tenant_id, config_id, title, item_type, path, sort_no) VALUES (?,?,?,?,?,?)",
                tid, configId, "首页", "VIEW", "/pages/index/index", 1);
        return R.ok();
    }

    @PostMapping("/{configId}/publish-mp")
    @RequirePermission("mp:edit")
    @Audited(module = "mpMenu", action = "publish", targetType = "menuConfig")
    public R<Map<String, Object>> publishMp(@PathVariable Long configId) {
        Long tid = TenantContext.requireTenantId();
        Map<String, Object> cfg = jdbc.queryForMap("SELECT * FROM sys_menu_config WHERE tenant_id=? AND id=?", tid, configId);
        if (!"MP".equals(cfg.get("end_type"))) throw new BizException("仅公众号菜单支持发布");
        Long accountId = jdbc.query("SELECT id FROM mp_account WHERE tenant_id=? AND deleted=0 LIMIT 1",
                rs -> rs.next() ? rs.getLong(1) : null, tid);
        if (accountId == null) throw new BizException("请先在公众号管理中完善凭证");
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("""
                    INSERT INTO mp_menu_publish(tenant_id, account_id, config_id, status, published_at)
                    VALUES (?,?,?,?,NOW())
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, tid);
            ps.setLong(2, accountId);
            ps.setLong(3, configId);
            ps.setString(4, "PUBLISHED");
            return ps;
        }, kh);
        return R.ok(Map.of("published", true, "publishId", Objects.requireNonNull(kh.getKey()).longValue(),
                "notice", "需重新进入公众号才可见"));
    }

    private void validate(String endType, List<Map<String, Object>> items) {
        if (items == null) throw new BizException("菜单不能为空");
        switch (endType) {
            case "MP" -> {
                if (items.size() > 3) throw new BizException("公众号一级菜单最多 3 个");
                for (Map<String, Object> item : items) {
                    List<?> children = (List<?>) item.get("children");
                    if (children != null && children.size() > 5) throw new BizException("公众号二级菜单最多 5 个");
                }
            }
            case "MINI_TABBAR" -> {
                if (items.size() < 2 || items.size() > 5) throw new BizException("小程序 tabBar 需 2~5 项");
            }
            case "H5" -> {
                if (items.size() > 5) throw new BizException("H5 底部导航最多 5 项");
            }
            default -> {
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void insertItems(Long tid, Long configId, Long parentId, List<Map<String, Object>> items) {
        int sort = 1;
        for (Map<String, Object> item : items) {
            KeyHolder kh = new GeneratedKeyHolder();
            int finalSort = sort++;
            jdbc.update(con -> {
                PreparedStatement ps = con.prepareStatement("""
                        INSERT INTO sys_menu_item(tenant_id, config_id, parent_id, title, icon, item_type, path, sort_no, visible, extra_json)
                        VALUES (?,?,?,?,?,?,?,?,?,?)
                        """, Statement.RETURN_GENERATED_KEYS);
                ps.setLong(1, tid);
                ps.setLong(2, configId);
                ps.setLong(3, parentId);
                ps.setString(4, str(item.get("title")));
                ps.setString(5, str(item.get("icon")));
                ps.setString(6, str(item.getOrDefault("itemType", "VIEW")));
                ps.setString(7, str(item.get("path")));
                ps.setInt(8, item.get("sortNo") instanceof Number n ? n.intValue() : finalSort);
                ps.setInt(9, item.get("visible") instanceof Number n ? n.intValue() : 1);
                ps.setString(10, str(item.get("extraJson")));
                return ps;
            }, kh);
            List<Map<String, Object>> children = (List<Map<String, Object>>) item.get("children");
            if (children != null && !children.isEmpty()) {
                insertItems(tid, configId, Objects.requireNonNull(kh.getKey()).longValue(), children);
            }
        }
    }

    private List<Map<String, Object>> tree(List<Map<String, Object>> rows, Long parentId) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            if (parentId.equals(((Number) row.get("parent_id")).longValue())) {
                Map<String, Object> node = new LinkedHashMap<>(row);
                node.put("children", tree(rows, ((Number) row.get("id")).longValue()));
                result.add(node);
            }
        }
        return result;
    }

    private String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
