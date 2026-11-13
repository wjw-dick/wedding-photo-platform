package com.wedding.biz.supplier;

import com.wedding.common.api.PageResult;
import com.wedding.common.api.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/supplier")
@RequiredArgsConstructor
public class SupplierPortalController {
    private final SupplierService supplierService;

    @PostMapping("/auth/login")
    public R<Map<String, Object>> login(@RequestBody Map<String, Object> body) {
        return R.ok(supplierService.login(String.valueOf(body.get("tenantCode")),
                String.valueOf(body.get("username")), String.valueOf(body.get("password"))));
    }

    @GetMapping("/orders")
    public R<PageResult<Map<String, Object>>> orders(@RequestParam(required = false) String status,
                                                     @RequestParam(defaultValue = "1") long page,
                                                     @RequestParam(defaultValue = "20") long size) {
        return R.ok(supplierService.myOrders(status, page, size));
    }

    @PostMapping("/orders/{id}/action")
    public R<Void> action(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        supplierService.action(id, String.valueOf(body.get("action")), (String) body.get("reason"),
                (String) body.get("trackingCompany"), (String) body.get("trackingNo"));
        return R.ok();
    }

    @GetMapping("/reconciliation")
    public R<Map<String, Object>> reconciliation(@RequestParam(required = false) String period) {
        return R.ok(supplierService.reconciliation(period));
    }

    @GetMapping("/qualifications")
    public R<List<Map<String, Object>>> qualifications() {
        return R.ok(supplierService.qualifications());
    }

    @PostMapping("/qualifications")
    public R<Long> addQualification(@RequestBody Map<String, Object> body) {
        return R.ok(supplierService.addQualification(String.valueOf(body.get("qualType")),
                (String) body.get("fileUrl"), (String) body.get("expireDate")));
    }
}
