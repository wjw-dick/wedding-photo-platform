package com.wedding.platform.controller;

import com.wedding.common.api.PageResult;
import com.wedding.common.api.R;
import com.wedding.common.audit.Audited;
import com.wedding.common.security.RequirePermission;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.web.bind.annotation.*;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/platform/announcements")
@RequiredArgsConstructor
public class PlatformAnnouncementController {
    private final JdbcTemplate jdbc;

    @GetMapping
    @RequirePermission("announcement:read")
    public R<PageResult<Map<String, Object>>> list(@RequestParam(defaultValue = "1") long page,
                                                   @RequestParam(defaultValue = "20") long size) {
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM platform_announcement WHERE deleted=0", Long.class);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM platform_announcement WHERE deleted=0 ORDER BY id DESC LIMIT ? OFFSET ?",
                size, (page - 1) * size);
        return R.ok(PageResult.of(total == null ? 0 : total, page, size, rows));
    }

    @PostMapping
    @RequirePermission("announcement:create")
    @Audited(module = "announcement", action = "create", targetType = "announcement")
    public R<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("""
                    INSERT INTO platform_announcement(title, content, target_type, target_value, status, publish_at)
                    VALUES (?,?,?,?,?,NOW())
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, (String) body.getOrDefault("title", ""));
            ps.setString(2, (String) body.getOrDefault("content", ""));
            ps.setString(3, (String) body.getOrDefault("targetType", "ALL"));
            ps.setString(4, (String) body.getOrDefault("targetValue", ""));
            ps.setString(5, (String) body.getOrDefault("status", "PUBLISHED"));
            return ps;
        }, kh);
        return R.ok(Map.of("id", Objects.requireNonNull(kh.getKey()).longValue()));
    }

    @PutMapping("/{id}")
    @RequirePermission("announcement:update")
    @Audited(module = "announcement", action = "update", targetType = "announcement")
    public R<Void> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        jdbc.update("UPDATE platform_announcement SET title=?, content=?, status=? WHERE id=?",
                body.get("title"), body.get("content"), body.getOrDefault("status", "PUBLISHED"), id);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @RequirePermission("announcement:delete")
    @Audited(module = "announcement", action = "delete", targetType = "announcement")
    public R<Void> delete(@PathVariable Long id) {
        jdbc.update("UPDATE platform_announcement SET deleted=1 WHERE id=?", id);
        return R.ok();
    }

    @GetMapping("/{id}/read-stats")
    @RequirePermission("announcement:read")
    public R<Map<String, Object>> readStats(@PathVariable Long id) {
        Long read = jdbc.queryForObject("SELECT COUNT(*) FROM platform_announcement_read WHERE announcement_id=?", Long.class, id);
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM platform_tenant WHERE deleted=0", Long.class);
        return R.ok(Map.of("read", read == null ? 0 : read, "total", total == null ? 0 : total));
    }
}
