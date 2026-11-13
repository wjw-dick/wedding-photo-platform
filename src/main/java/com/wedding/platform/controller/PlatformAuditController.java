package com.wedding.platform.controller;

import com.wedding.common.api.PageResult;
import com.wedding.common.api.R;
import com.wedding.common.security.RequirePermission;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/platform/audits")
@RequiredArgsConstructor
public class PlatformAuditController {
    private final JdbcTemplate jdbc;

    @GetMapping
    @RequirePermission("audit:read")
    public R<PageResult<Map<String, Object>>> list(@RequestParam(required = false) Long tenantId,
                                                   @RequestParam(required = false) String action,
                                                   @RequestParam(defaultValue = "1") long page,
                                                   @RequestParam(defaultValue = "20") long size) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new java.util.ArrayList<>();
        if (tenantId != null) {
            where.append(" AND target_tenant_id=?");
            args.add(tenantId);
        }
        if (action != null && !action.isBlank()) {
            where.append(" AND action LIKE ?");
            args.add("%" + action + "%");
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM platform_audit" + where, Long.class, args.toArray());
        args.add(size);
        args.add((page - 1) * size);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM platform_audit" + where + " ORDER BY id DESC LIMIT ? OFFSET ?", args.toArray());
        return R.ok(PageResult.of(total == null ? 0 : total, page, size, rows));
    }
}
