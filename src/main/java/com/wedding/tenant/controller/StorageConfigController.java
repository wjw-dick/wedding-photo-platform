package com.wedding.tenant.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wedding.common.api.BizException;
import com.wedding.common.api.R;
import com.wedding.common.audit.Audited;
import com.wedding.common.context.TenantContext;
import com.wedding.common.security.RequirePermission;
import com.wedding.common.storage.StorageConfig;
import com.wedding.common.storage.StorageManager;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/tenant/storage")
@RequiredArgsConstructor
public class StorageConfigController {
    private final StorageManager storageManager;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    @GetMapping
    @RequirePermission("system:view")
    public R<Map<String, Object>> configs() {
        Long tid = TenantContext.requireTenantId();
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, provider, config_json, active_flag FROM storage_config WHERE tenant_id=?", tid);
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            StorageConfig cfg = storageManager.configOf(tid, String.valueOf(row.get("provider")));
            list.add(Map.of("id", row.get("id"), "provider", row.get("provider"),
                    "active", row.get("active_flag"), "endpoint", safe(cfg.getEndpoint()),
                    "bucket", safe(cfg.getBucket()), "region", safe(cfg.getRegion()),
                    "accessKey", mask(cfg.getAccessKey()), "secretKey", mask(cfg.getSecretKey())));
        }
        StorageConfig active = storageManager.activeConfig(tid);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("activeProvider", active.getProvider());
        result.put("configs", list);
        return R.ok(result);
    }

    @PostMapping("/test")
    @RequirePermission("system:edit")
    public R<Map<String, Object>> test(@RequestBody StorageConfig cfg) {
        if (cfg.getProvider() == null) throw new BizException("请选择存储驱动");
        storageManager.testConnection(cfg);
        return R.ok(Map.of("ok", true, "message", "连接测试通过"));
    }

    @PostMapping("/save")
    @RequirePermission("system:edit")
    @Audited(module = "storage", action = "save", targetType = "storage")
    public R<Void> save(@RequestBody SaveReq req) {
        Long tid = TenantContext.requireTenantId();
        boolean switching = !req.provider().equals(storageManager.activeConfig(tid).getProvider());
        if (switching && !Boolean.TRUE.equals(req.confirmSwitch())) {
            throw new BizException("切换存储驱动需二次确认");
        }
        StorageConfig cfg = req.config();
        cfg.setProvider(req.provider());
        storageManager.testConnection(cfg);
        storageManager.saveConfig(tid, cfg, true);
        return R.ok();
    }

    @PostMapping("/{provider}/activate")
    @RequirePermission("system:edit")
    @Audited(module = "storage", action = "activate", targetType = "storage")
    public R<Void> activate(@PathVariable String provider, @RequestBody Map<String, Boolean> body) {
        if (!Boolean.TRUE.equals(body.get("confirm"))) throw new BizException("切换存储驱动需二次确认");
        Long tid = TenantContext.requireTenantId();
        StorageConfig cfg = storageManager.configOf(tid, provider);
        storageManager.saveConfig(tid, cfg, true);
        return R.ok();
    }

    private String mask(String v) {
        if (v == null || v.isBlank()) return "";
        if (v.length() <= 4) return "****";
        return v.substring(0, 2) + "****" + v.substring(v.length() - 2);
    }

    private String safe(String v) {
        return v == null ? "" : v;
    }

    public record SaveReq(String provider, StorageConfig config, Boolean confirmSwitch) {
    }
}
