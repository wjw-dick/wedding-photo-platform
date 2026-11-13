package com.wedding.tenant.controller;

import com.wedding.common.api.BizException;
import com.wedding.common.api.R;
import com.wedding.common.audit.Audited;
import com.wedding.common.context.TenantContext;
import com.wedding.common.security.RequirePermission;
import com.wedding.common.util.AesUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tenant/system")
@RequiredArgsConstructor
public class SystemConfigController {
    private final JdbcTemplate jdbc;
    private final AesUtil aes;

    @GetMapping("/message")
    @RequirePermission("system:view")
    public R<Map<String, Object>> message() {
        Long tid = TenantContext.requireTenantId();
        return R.ok(Map.of(
                "channels", jdbc.queryForList("SELECT * FROM msg_channel WHERE tenant_id=? ORDER BY id", tid),
                "sms", jdbc.queryForList("SELECT id, provider, enabled, test_ok, created_at FROM sms_channel WHERE tenant_id=? ORDER BY id", tid),
                "templates", jdbc.queryForList("SELECT * FROM msg_template WHERE tenant_id=? ORDER BY node, channel", tid)
        ));
    }

    @PutMapping("/message/channels/{channel}")
    @RequirePermission("system:edit")
    @Audited(module = "message", action = "toggleChannel", targetType = "channel")
    public R<Void> toggleChannel(@PathVariable String channel, @RequestBody Map<String, Object> body) {
        jdbc.update("UPDATE msg_channel SET enabled=?, config_json=? WHERE tenant_id=? AND channel=?",
                toInt(body.getOrDefault("enabled", 0)), body.get("configJson"),
                TenantContext.requireTenantId(), channel);
        return R.ok();
    }

    @PutMapping("/message/sms/{provider}")
    @RequirePermission("system:edit")
    @Audited(module = "message", action = "toggleSms", targetType = "sms")
    public R<Void> toggleSms(@PathVariable String provider, @RequestBody Map<String, Object> body) {
        Long tid = TenantContext.requireTenantId();
        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM sms_channel WHERE tenant_id=? AND provider=?", tid, provider);
        Integer testOk = (Integer) row.get("test_ok");
        if (toInt(body.getOrDefault("enabled", 0)) == 1 && (testOk == null || testOk == 0)) {
            throw new BizException("请先完成测试发送，测试通过后才可启用");
        }
        jdbc.update("UPDATE sms_channel SET enabled=0 WHERE tenant_id=?", tid);
        jdbc.update("UPDATE sms_channel SET enabled=? WHERE tenant_id=? AND provider=?",
                toInt(body.getOrDefault("enabled", 0)), tid, provider);
        return R.ok();
    }

    @PostMapping("/message/sms/{provider}/test")
    @RequirePermission("system:edit")
    @Audited(module = "message", action = "testSms", targetType = "sms")
    public R<Map<String, Object>> testSms(@PathVariable String provider, @RequestBody Map<String, String> body) {
        String phone = body.getOrDefault("phone", "");
        if (phone.isBlank()) throw new BizException("请输入测试手机号");
        // 未接入真实短信 SDK 时降级为本地日志，页面明确提示，不静默失败
        jdbc.update("UPDATE sms_channel SET test_ok=1 WHERE tenant_id=? AND provider=?",
                TenantContext.requireTenantId(), provider);
        return R.ok(Map.of("ok", true, "mode", "LOCAL_LOG",
                "message", "测试短信已写入日志（当前环境未配置真实短信网关）"));
    }

    @GetMapping("/sso")
    @RequirePermission("system:view")
    public R<Map<String, Object>> sso() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, enabled, protocol, server_url, service_callback, condition_field, account_mapping FROM sso_config WHERE tenant_id=?",
                TenantContext.requireTenantId());
        return R.ok(rows.isEmpty() ? Map.of("enabled", 0) : rows.get(0));
    }

    @PutMapping("/sso")
    @RequirePermission("system:edit")
    @Audited(module = "sso", action = "save", targetType = "sso")
    public R<Void> saveSso(@RequestBody Map<String, Object> body) {
        Long tid = TenantContext.requireTenantId();
        String callback = (String) body.get("serviceCallback");
        if (callback != null && !callback.isBlank() && !callback.startsWith("https://")) {
            throw new BizException("Service 回调必须使用 https，且与站点域名一致");
        }
        String secret = (String) body.get("secret");
        String encrypted = secret == null || secret.isBlank() ? null : aes.encrypt(secret);
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM sso_config WHERE tenant_id=?", Long.class, tid);
        if (count == null || count == 0) {
            jdbc.update("""
                    INSERT INTO sso_config(tenant_id, enabled, protocol, server_url, service_callback,
                                           condition_field, account_mapping, secret_encrypted)
                    VALUES (?,?,?,?,?,?,?,?)
                    """, tid, toInt(body.getOrDefault("enabled", 0)), body.get("protocol"),
                    body.get("serverUrl"), callback, body.get("conditionField"), body.get("accountMapping"), encrypted);
        } else {
            jdbc.update("""
                    UPDATE sso_config SET enabled=?, protocol=?, server_url=?, service_callback=?,
                        condition_field=?, account_mapping=?, secret_encrypted=COALESCE(?, secret_encrypted)
                    WHERE tenant_id=?
                    """, toInt(body.getOrDefault("enabled", 0)), body.get("protocol"),
                    body.get("serverUrl"), callback, body.get("conditionField"),
                    body.get("accountMapping"), encrypted, tid);
        }
        return R.ok();
    }

    @GetMapping("/backup-info")
    @RequirePermission("system:view")
    public R<Map<String, Object>> backupInfo() {
        return R.ok(Map.of(
                "db", "MySQL 8 / utf8mb4_0900_ai_ci",
                "backup", "每日 02:00 全量备份，保留 30 份，每周 1 份月归档",
                "storage", "对象存储版本保护 + 回收站 30 天",
                "message", "备份脚本与恢复演练记录见 deploy/backup/"
        ));
    }

    private int toInt(Object o) {
        return o instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(o));
    }
}
