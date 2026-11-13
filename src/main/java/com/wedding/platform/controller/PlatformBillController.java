package com.wedding.platform.controller;

import com.wedding.common.api.PageResult;
import com.wedding.common.api.R;
import com.wedding.common.audit.Audited;
import com.wedding.common.security.RequirePermission;
import com.wedding.platform.entity.PlatformBill;
import com.wedding.platform.service.BillService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/api/platform/bills")
@RequiredArgsConstructor
public class PlatformBillController {
    private final BillService billService;

    @GetMapping
    @RequirePermission("bill:read")
    public R<PageResult<PlatformBill>> list(@RequestParam(required = false) Long tenantId,
                                            @RequestParam(required = false) String period,
                                            @RequestParam(required = false) String status,
                                            @RequestParam(defaultValue = "1") long page,
                                            @RequestParam(defaultValue = "20") long size) {
        return R.ok(billService.list(tenantId, period, status, page, size));
    }

    @PostMapping("/generate")
    @RequirePermission("bill:create")
    @Audited(module = "bill", action = "generate", targetType = "bill")
    public R<Map<String, Object>> generate(@RequestBody(required = false) Map<String, String> body) {
        String period = body == null || body.get("period") == null ? billService.currentPeriod() : body.get("period");
        int created = billService.generate(period);
        return R.ok(Map.of("period", period, "created", created));
    }

    @PostMapping("/{id}/paid")
    @RequirePermission("bill:update")
    @Audited(module = "bill", action = "paid", targetType = "bill")
    public R<Void> paid(@PathVariable Long id) {
        billService.markPaid(id);
        return R.ok();
    }

    @GetMapping("/export")
    @RequirePermission("bill:read")
    @Audited(module = "bill", action = "export", targetType = "bill")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) String period) {
        byte[] bytes = billService.exportCsv(period).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=bills.csv")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(bytes);
    }
}
