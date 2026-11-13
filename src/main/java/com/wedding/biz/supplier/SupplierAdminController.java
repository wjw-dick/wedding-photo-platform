package com.wedding.biz.supplier;

import com.wedding.common.api.PageResult;
import com.wedding.common.api.R;
import com.wedding.common.security.RequirePermission;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/tenant/supplier")
@RequiredArgsConstructor
public class SupplierAdminController {
    private final SupplierService supplierService;

    @GetMapping("/suppliers")
    @RequirePermission("supplier:view")
    public R<PageResult<Map<String, Object>>> suppliers(@RequestParam(required = false) String keyword,
                                                        @RequestParam(defaultValue = "1") long page,
                                                        @RequestParam(defaultValue = "20") long size) {
        return R.ok(supplierService.suppliers(keyword, page, size));
    }

    @PostMapping("/suppliers/{id}/account")
    @RequirePermission("supplier:edit")
    public R<Map<String, Object>> account(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return R.ok(supplierService.createAccount(id, String.valueOf(body.get("username")),
                String.valueOf(body.get("password"))));
    }

    @GetMapping("/statement")
    @RequirePermission("supplier:view")
    public R<Map<String, Object>> statement(@RequestParam(required = false) String period) {
        return R.ok(supplierService.monthlyStatement(period));
    }

    @GetMapping("/qualifications/expiring")
    @RequirePermission("supplier:view")
    public R<Map<String, Object>> expiring() {
        return R.ok(supplierService.expiringQualifications());
    }
}
