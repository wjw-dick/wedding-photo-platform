package com.wedding.platform.controller;

import com.wedding.common.api.PageResult;
import com.wedding.common.api.R;
import com.wedding.common.security.RequirePermission;
import com.wedding.platform.service.PlatformTenantService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/platform/tenants")
@RequiredArgsConstructor
public class PlatformTenantController {
    private final PlatformTenantService tenantService;

    @GetMapping
    @RequirePermission("tenant:read")
    public R<PageResult<Map<String, Object>>> list(@RequestParam(required = false) String keyword,
                                                   @RequestParam(required = false) String status,
                                                   @RequestParam(defaultValue = "1") long page,
                                                   @RequestParam(defaultValue = "20") long size) {
        return R.ok(tenantService.list(keyword, status, page, size));
    }

    @GetMapping("/{tenantId}")
    @RequirePermission("tenant:read")
    public R<Map<String, Object>> detail(@PathVariable Long tenantId) {
        return R.ok(tenantService.detail(tenantId));
    }

    @PostMapping
    @RequirePermission("tenant:create")
    public R<Map<String, Object>> create(@RequestBody PlatformTenantService.CreateTenantReq req) {
        return R.ok(tenantService.create(req));
    }

    @PostMapping("/{tenantId}/status")
    @RequirePermission("tenant:update")
    public R<Void> status(@PathVariable Long tenantId, @RequestBody Map<String, String> body) {
        tenantService.changeStatus(tenantId, body.get("status"));
        return R.ok();
    }

    @PostMapping("/{tenantId}/renew")
    @RequirePermission("tenant:update")
    public R<Void> renew(@PathVariable Long tenantId, @RequestBody Map<String, String> body) {
        tenantService.renew(tenantId, LocalDateTime.parse(body.get("expireAt")));
        return R.ok();
    }

    @PutMapping("/{tenantId}/quota")
    @RequirePermission("tenant:quota")
    public R<Void> quota(@PathVariable Long tenantId, @RequestBody Map<String, Object> body) {
        tenantService.updateQuota(tenantId,
                intOrNull(body.get("seatLimit")), intOrNull(body.get("mpLimit")),
                intOrNull(body.get("storageGb")), intOrNull(body.get("smsQuota")), longOrNull(body.get("apiQuota")));
        return R.ok();
    }

    @DeleteMapping("/{tenantId}")
    @RequirePermission("tenant:delete")
    public R<Void> delete(@PathVariable Long tenantId) {
        tenantService.delete(tenantId);
        return R.ok();
    }


    @GetMapping("/export")
    @RequirePermission("tenant:read")
    public org.springframework.http.ResponseEntity<byte[]> export(@RequestParam(required = false) String status) {
        var rows = tenantService.list(null, status, 1, 10000).getRecords();
        StringBuilder sb = new StringBuilder("\uFEFF租户编码,租户名称,套餐,状态,到期时间,已用席位,席位上限\n");
        for (Map<String, Object> r : rows) {
            sb.append(r.getOrDefault("tenantCode", "")).append(',')
              .append(r.getOrDefault("tenantName", "")).append(',')
              .append(r.getOrDefault("planName", "")).append(',')
              .append(r.getOrDefault("status", "")).append(',')
              .append(r.getOrDefault("expireAt", "")).append(',')
              .append(r.getOrDefault("usedSeat", "")).append(',')
              .append(r.getOrDefault("seatLimit", "")).append('\n');
        }
        byte[] bytes = sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return org.springframework.http.ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"tenants.csv\"")
                .contentType(new org.springframework.http.MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
                .body(bytes);
    }
    private Integer intOrNull(Object v) {
        return v instanceof Number n ? n.intValue() : null;
    }

    private Long longOrNull(Object v) {
        return v instanceof Number n ? n.longValue() : null;
    }
}
