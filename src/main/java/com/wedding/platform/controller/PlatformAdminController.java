package com.wedding.platform.controller;

import com.wedding.common.api.R;
import com.wedding.common.audit.Audited;
import com.wedding.common.security.RequirePermission;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/platform/admins")
@RequiredArgsConstructor
public class PlatformAdminController {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;

    @GetMapping
    @RequirePermission("platform:admin")
    public R<List<Map<String, Object>>> list() {
        return R.ok(jdbc.queryForList("""
                SELECT id, username, real_name, role, status, last_login_at, created_at
                FROM platform_admin WHERE deleted=0 ORDER BY id
                """));
    }

    @PostMapping
    @RequirePermission("platform:admin")
    @Audited(module = "platformAdmin", action = "create", targetType = "admin")
    public R<Void> create(@RequestBody Map<String, String> body) {
        String role = body.getOrDefault("role", "PLATFORM_OPERATOR");
        if (!List.of("PLATFORM_ADMIN", "PLATFORM_OPERATOR", "PLATFORM_FINANCE").contains(role)) {
            return R.fail(400, "非法角色");
        }
        jdbc.update("INSERT INTO platform_admin(username, password, real_name, role, status) VALUES (?,?,?,?,1)",
                body.get("username"), passwordEncoder.encode(body.getOrDefault("password", "Admin@123456")),
                body.getOrDefault("realName", body.get("username")), role);
        return R.ok();
    }

    @PostMapping("/{id}/status")
    @RequirePermission("platform:admin")
    @Audited(module = "platformAdmin", action = "status", targetType = "admin")
    public R<Void> status(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        jdbc.update("UPDATE platform_admin SET status=? WHERE id=?", body.getOrDefault("status", 0), id);
        return R.ok();
    }
}
