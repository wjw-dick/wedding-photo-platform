package com.wedding.biz.order;

import com.wedding.common.api.PageResult;
import com.wedding.common.api.R;
import com.wedding.common.audit.Audited;
import com.wedding.common.security.RequirePermission;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tenant/orders")
@RequiredArgsConstructor
public class OrderController {
    private final OrderService orderService;

    @GetMapping
    @RequirePermission("order:view")
    public R<PageResult<Map<String, Object>>> list(@RequestParam(required = false) String keyword,
                                                   @RequestParam(required = false) String status,
                                                   @RequestParam(required = false) Long storeId,
                                                   @RequestParam(required = false) String from,
                                                   @RequestParam(required = false) String to,
                                                   @RequestParam(defaultValue = "1") long page,
                                                   @RequestParam(defaultValue = "20") long size) {
        return R.ok(orderService.list(keyword, status, storeId, from, to, page, size));
    }

    @GetMapping("/{id}")
    @RequirePermission("order:view")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        return R.ok(orderService.detail(id));
    }

    @PostMapping
    @RequirePermission("order:edit")
    @Audited(module = "order", action = "create", targetType = "order")
    public R<Map<String, Object>> create(@RequestBody OrderService.CreateOrderReq req) {
        return R.ok(orderService.create(req));
    }

    @PostMapping("/{id}/transition")
    @RequirePermission("order:edit")
    @Audited(module = "order", action = "transition", targetType = "order")
    public R<Void> transition(@PathVariable Long id, @RequestBody Map<String, String> body) {
        orderService.transition(id, body.get("to"), body.get("remark"));
        return R.ok();
    }

    @PostMapping("/{orderNo}/pay")
    @RequirePermission("order:edit")
    @Audited(module = "order", action = "pay", targetType = "order")
    public R<Map<String, Object>> pay(@PathVariable String orderNo, @RequestBody Map<String, Object> body) {
        return R.ok(orderService.pay(orderNo,
                String.valueOf(body.getOrDefault("payNo", System.currentTimeMillis())),
                String.valueOf(body.getOrDefault("payType", "DEPOSIT")),
                String.valueOf(body.getOrDefault("channel", "OFFLINE")),
                new BigDecimal(String.valueOf(body.getOrDefault("amount", "0"))),
                String.valueOf(body), null));
    }

    @PostMapping("/{id}/refund")
    @RequirePermission("order:edit")
    @Audited(module = "order", action = "refundApply", targetType = "order")
    public R<Map<String, Object>> refund(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return R.ok(orderService.applyRefund(id, new BigDecimal(String.valueOf(body.get("amount"))),
                String.valueOf(body.getOrDefault("reason", ""))));
    }

    @PostMapping("/refunds/{refundId}/audit")
    @RequirePermission("order:edit")
    @Audited(module = "order", action = "refundAudit", targetType = "refund")
    public R<Void> auditRefund(@PathVariable Long refundId, @RequestBody Map<String, Object> body) {
        orderService.auditRefund(refundId, Boolean.TRUE.equals(body.get("approve")),
                String.valueOf(body.getOrDefault("remark", "")));
        return R.ok();
    }

    @PostMapping("/{id}/contract-sign")
    @RequirePermission("order:edit")
    @Audited(module = "order", action = "contractSign", targetType = "contract")
    public R<Void> sign(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        orderService.signContract(id,
                body.get("contractId") == null ? null : Long.valueOf(String.valueOf(body.get("contractId"))),
                (String) body.get("signImage"), (String) body.get("pdfUrl"));
        return R.ok();
    }
}
