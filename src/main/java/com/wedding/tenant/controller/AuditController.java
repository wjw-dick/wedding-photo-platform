package com.wedding.tenant.controller;

import com.wedding.common.api.PageResult;
import com.wedding.common.api.R;
import com.wedding.common.context.TenantContext;
import com.wedding.common.security.RequirePermission;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 审计日志只读：只提供列表/详情/导出，不提供任何写入与删除入口。 */
@RestController
@RequestMapping("/api/tenant/audits")
@RequiredArgsConstructor
public class AuditController {
    private final JdbcTemplate jdbc;

    @GetMapping
    @RequirePermission("system:audit")
    public R<PageResult<Map<String, Object>>> list(@RequestParam(required = false) Long storeId,
                                                   @RequestParam(required = false) String module,
                                                   @RequestParam(required = false) String operator,
                                                   @RequestParam(required = false) String from,
                                                   @RequestParam(required = false) String to,
                                                   @RequestParam(defaultValue = "1") long page,
                                                   @RequestParam(defaultValue = "20") long size) {
        Long tid = TenantContext.requireTenantId();
        StringBuilder where = new StringBuilder(" WHERE tenant_id=?");
        List<Object> args = new ArrayList<>();
        args.add(tid);
        if (storeId != null) {
            where.append(" AND store_id=?");
            args.add(storeId);
        }
        if (module != null && !module.isBlank()) {
            where.append(" AND module=?");
            args.add(module);
        }
        if (operator != null && !operator.isBlank()) {
            where.append(" AND operator_name LIKE ?");
            args.add("%" + operator + "%");
        }
        if (from != null && !from.isBlank()) {
            where.append(" AND created_at >= ?");
            args.add(from + " 00:00:00");
        }
        if (to != null && !to.isBlank()) {
            where.append(" AND created_at <= ?");
            args.add(to + " 23:59:59");
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM sys_audit" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add((page - 1) * size);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM sys_audit" + where + " ORDER BY id DESC LIMIT ? OFFSET ?", pageArgs.toArray());
        return R.ok(PageResult.of(total == null ? 0 : total, page, size, rows));
    }

    @GetMapping("/{id}")
    @RequirePermission("system:audit")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        return R.ok(jdbc.queryForMap("SELECT * FROM sys_audit WHERE tenant_id=? AND id=?",
                TenantContext.requireTenantId(), id));
    }

    @GetMapping("/export")
    @RequirePermission("system:audit")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) String from,
                                         @RequestParam(required = false) String to) {
        Long tid = TenantContext.requireTenantId();
        StringBuilder sql = new StringBuilder("SELECT * FROM sys_audit WHERE tenant_id=?");
        List<Object> args = new ArrayList<>();
        args.add(tid);
        if (from != null && !from.isBlank()) {
            sql.append(" AND created_at >= ?");
            args.add(from + " 00:00:00");
        }
        if (to != null && !to.isBlank()) {
            sql.append(" AND created_at <= ?");
            args.add(to + " 23:59:59");
        }
        sql.append(" ORDER BY id DESC LIMIT 50000");
        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());
        StringBuilder csv = new StringBuilder("id,operator,module,action,target,result,ip,costMs,createdAt\n");
        for (Map<String, Object> r : rows) {
            csv.append(r.get("id")).append(',').append(r.get("operator_name")).append(',')
                    .append(r.get("module")).append(',').append(r.get("action")).append(',')
                    .append(r.get("target_type")).append(':').append(r.get("target_id")).append(',')
                    .append(r.get("result")).append(',').append(r.get("ip")).append(',')
                    .append(r.get("cost_ms")).append(',').append(r.get("created_at")).append('\n');
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=audit.csv")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }
}
