package com.wedding.open;

import com.wedding.biz.order.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

/** 支付回调：幂等键 = 订单号 + 支付流水号 + 金额，重复通知直接返回成功。 */
@RestController
@RequestMapping("/api/open/pay/{tenantCode}")
@RequiredArgsConstructor
public class OpenPayController {
    private final TenantResolver tenantResolver;
    private final OrderService orderService;

    @PostMapping("/callback")
    public Map<String, Object> callback(@PathVariable String tenantCode, @RequestBody Map<String, Object> body) {
        tenantResolver.byCode(tenantCode);
        String orderNo = String.valueOf(body.get("orderNo"));
        String payNo = String.valueOf(body.get("payNo"));
        BigDecimal amount = new BigDecimal(String.valueOf(body.get("amount")));
        Map<String, Object> result = orderService.pay(orderNo, payNo,
                String.valueOf(body.getOrDefault("payType", "DEPOSIT")),
                String.valueOf(body.getOrDefault("channel", "WECHAT")),
                amount, String.valueOf(body), "callback");
        return Map.of("code", "SUCCESS", "message", "OK", "data", result);
    }
}
