package com.wedding.open;

import com.wedding.biz.order.OrderService;
import com.wedding.biz.production.ProductionService;
import com.wedding.common.api.BizException;
import com.wedding.common.api.R;
import com.wedding.common.context.LoginContext;
import com.wedding.common.context.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/member")
@RequiredArgsConstructor
public class MemberController {
    private final MemberService memberService;
    private final OrderService orderService;
    private final ProductionService productionService;
    private final JdbcTemplate jdbc;
    private final com.wedding.biz.order.OrderExtraService orderExtraService;

    @GetMapping("/profile")
    public R<Map<String, Object>> profile() {
        return R.ok(memberService.profile());
    }

    @GetMapping("/orders")
    public R<List<Map<String, Object>>> orders() {
        return R.ok(memberService.myOrders());
    }

    @GetMapping("/orders/{id}")
    public R<Map<String, Object>> order(@PathVariable Long id) {
        return R.ok(memberService.myOrder(id));
    }

    @PostMapping("/orders")
    public R<Map<String, Object>> createOrder(@RequestBody OrderService.CreateOrderReq req) {
        Long memberId = LoginContext.require().getId();
        OrderService.CreateOrderReq withMember = new OrderService.CreateOrderReq(
                req.storeId(), memberId, req.customerName(), req.customerPhone(), req.packageId(),
                req.shootDate(), req.shootStart(), req.shootEnd(), req.resourceIds(), req.addons(),
                req.channelId(), req.salesmanId(), "ONLINE", req.remark());
        return R.ok(orderService.create(withMember));
    }

    @PostMapping("/orders/{orderNo}/pay")
    public R<Map<String, Object>> pay(@PathVariable String orderNo, @RequestBody Map<String, Object> body) {
        Long memberId = LoginContext.require().getId();
        Long owner = jdbc.queryForObject("SELECT member_id FROM order_main WHERE tenant_id=? AND order_no=?",
                Long.class, TenantContext.requireTenantId(), orderNo);
        if (owner == null || !owner.equals(memberId)) throw BizException.forbidden("无权支付该订单");
        return R.ok(orderService.pay(orderNo,
                String.valueOf(body.getOrDefault("payNo", System.currentTimeMillis())),
                String.valueOf(body.getOrDefault("payType", "DEPOSIT")),
                String.valueOf(body.getOrDefault("channel", "WECHAT")),
                new BigDecimal(String.valueOf(body.getOrDefault("amount", "0"))),
                String.valueOf(body), null));
    }

    @GetMapping("/orders/{orderId}/selection")
    public R<Map<String, Object>> selection(@PathVariable Long orderId) {
        ensureOwner(orderId);
        return R.ok(productionService.selectionSheet(orderId));
    }

    @PostMapping("/orders/{orderId}/selection/submit")
    public R<Map<String, Object>> submitSelection(@PathVariable Long orderId, @RequestBody Map<String, Object> body) {
        ensureOwner(orderId);
        @SuppressWarnings("unchecked")
        List<Number> ids = (List<Number>) body.get("mediaIds");
        return R.ok(productionService.submitSelection(orderId,
                ids == null ? List.of() : ids.stream().map(Number::longValue).toList(),
                (String) body.get("signImage")));
    }

    @PostMapping("/orders/{orderId}/contract-sign")
    public R<Void> signContract(@PathVariable Long orderId, @RequestBody Map<String, Object> body) {
        ensureOwner(orderId);
        orderService.signContract(orderId,
                body.get("contractId") == null ? null : Long.valueOf(String.valueOf(body.get("contractId"))),
                (String) body.get("signImage"), (String) body.get("pdfUrl"));
        return R.ok();
    }

    private void ensureOwner(Long orderId) {
        Long memberId = LoginContext.require().getId();
        Long owner = jdbc.queryForObject("SELECT member_id FROM order_main WHERE tenant_id=? AND id=?",
                Long.class, TenantContext.requireTenantId(), orderId);
        if (owner == null || !owner.equals(memberId)) throw BizException.forbidden("无权访问该订单");
    }

    @GetMapping("/addresses")
    public R<List<Map<String, Object>>> addresses() {
        return R.ok(memberService.addresses());
    }

    @PostMapping("/addresses")
    public R<Long> saveAddress(@RequestBody Map<String, Object> body) {
        return R.ok(memberService.saveAddress(body));
    }

    @DeleteMapping("/addresses/{id}")
    public R<Void> deleteAddress(@PathVariable Long id) {
        memberService.deleteAddress(id);
        return R.ok();
    }

    @GetMapping("/coupons")
    public R<List<Map<String, Object>>> coupons() {
        return R.ok(memberService.myCoupons());
    }

    @GetMapping("/notices")
    public R<List<Map<String, Object>>> notices() {
        return R.ok(memberService.myNotices());
    }

    @GetMapping("/deliveries")
    public R<List<Map<String, Object>>> deliveries() {
        return R.ok(memberService.myDeliveries());
    }

    @GetMapping("/orders/{orderId}/downloads")
    public R<Map<String, Object>> downloads(@PathVariable Long orderId) {
        return R.ok(memberService.myDownloads(orderId));
    }

    @PostMapping("/invoices")
    public R<Long> invoice(@RequestBody Map<String, Object> body) {
        Long orderId = Long.valueOf(String.valueOf(body.get("orderId")));
        ensureOwner(orderId);
        return R.ok(orderExtraService.applyInvoice(orderId, (String) body.get("titleType"),
                (String) body.get("title"), (String) body.get("taxNo"),
                new BigDecimal(String.valueOf(body.getOrDefault("amount", "0")))));
    }

    @PostMapping("/subscribe-message")
    public R<Map<String, Object>> subscribe(@RequestBody Map<String, Object> body) {
        Long tid = TenantContext.requireTenantId();
        jdbc.update("""
                INSERT INTO sys_notice(tenant_id, title, content, channel) VALUES (?,?,?, 'SITE')
                """, tid, "订阅消息授权", "会员 " + LoginContext.require().getId() + " 订阅节点：" +
                body.getOrDefault("nodes", ""));
        return R.ok(Map.of("ok", true));
    }
}
