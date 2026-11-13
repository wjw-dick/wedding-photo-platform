package com.wedding.biz.order;

import com.wedding.common.api.PageResult;
import com.wedding.common.api.R;
import com.wedding.common.security.RequirePermission;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tenant/order-extra")
@RequiredArgsConstructor
public class OrderExtraController {
    private final OrderExtraService service;

    @PostMapping("/orders/{id}/reschedule")
    @RequirePermission("order:edit")
    public R<Map<String, Object>> reschedule(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Number> ids = (List<Number>) body.get("resourceIds");
        return R.ok(service.reschedule(id, (String) body.get("newDate"), (String) body.get("newStart"),
                (String) body.get("newEnd"), ids == null ? List.of() : ids.stream().map(Number::longValue).toList(),
                (String) body.get("reason"), (String) body.get("diffRule")));
    }

    @PostMapping("/orders/{id}/cancel")
    @RequirePermission("order:edit")
    public R<Void> cancel(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        service.cancel(id, (String) body.get("reason"),
                Boolean.parseBoolean(String.valueOf(body.getOrDefault("refundDeposit", false))));
        return R.ok();
    }

    @GetMapping("/contract-templates")
    @RequirePermission("order:view")
    public R<List<Map<String, Object>>> templates() {
        return R.ok(service.templates());
    }

    @PostMapping("/contract-templates")
    @RequirePermission("order:edit")
    public R<Long> saveTemplate(@RequestBody Map<String, Object> body) {
        return R.ok(service.saveTemplate(body));
    }

    @PostMapping("/orders/{id}/contract")
    @RequirePermission("order:edit")
    public R<Map<String, Object>> contract(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Object raw = body.get("templateId");
        Long templateId = raw == null || String.valueOf(raw).isBlank() || "null".equals(String.valueOf(raw))
                ? null : Long.valueOf(String.valueOf(raw));
        return R.ok(service.prepareContract(id, templateId));
    }

    @GetMapping("/invoices")
    @RequirePermission("order:view")
    public R<PageResult<Map<String, Object>>> invoices(@RequestParam(required = false) String status,
                                                       @RequestParam(defaultValue = "1") long page,
                                                       @RequestParam(defaultValue = "20") long size) {
        return R.ok(service.invoices(status, page, size));
    }

    @PostMapping("/invoices")
    @RequirePermission("order:edit")
    public R<Long> applyInvoice(@RequestBody Map<String, Object> body) {
        return R.ok(service.applyInvoice(Long.valueOf(String.valueOf(body.get("orderId"))),
                (String) body.get("titleType"), (String) body.get("title"), (String) body.get("taxNo"),
                new BigDecimal(String.valueOf(body.getOrDefault("amount", "0")))));
    }

    @PostMapping("/invoices/{id}/issue")
    @RequirePermission("order:edit")
    public R<Void> issue(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        service.issueInvoice(id, String.valueOf(body.get("invoiceNo")), (String) body.get("remark"));
        return R.ok();
    }
}
