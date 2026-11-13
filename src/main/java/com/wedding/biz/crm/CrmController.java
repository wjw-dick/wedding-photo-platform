package com.wedding.biz.crm;

import com.wedding.common.api.PageResult;
import com.wedding.common.api.R;
import com.wedding.common.security.RequirePermission;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tenant/crm")
@RequiredArgsConstructor
public class CrmController {
    private final CrmService crmService;

    @GetMapping("/leads")
    @RequirePermission("lead:view")
    public R<PageResult<Map<String, Object>>> leads(@RequestParam(required = false) String keyword,
                                                    @RequestParam(required = false) String status,
                                                    @RequestParam(required = false) Long storeId,
                                                    @RequestParam(required = false) Long ownerId,
                                                    @RequestParam(defaultValue = "1") long page,
                                                    @RequestParam(defaultValue = "20") long size) {
        return R.ok(crmService.leads(keyword, status, storeId, ownerId, page, size));
    }

    @PostMapping("/leads")
    @RequirePermission("lead:edit")
    public R<Long> createLead(@RequestBody Map<String, Object> body) {
        return R.ok(crmService.createLead(body));
    }

    @GetMapping("/leads/{id}")
    @RequirePermission("lead:view")
    public R<Map<String, Object>> lead(@PathVariable Long id) {
        return R.ok(crmService.leadDetail(id));
    }

    @PostMapping("/leads/{id}/follow")
    @RequirePermission("lead:edit")
    public R<Void> follow(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        crmService.follow(id, (String) body.get("followType"), (String) body.get("content"),
                (String) body.get("nextFollowAt"));
        return R.ok();
    }

    @PostMapping("/leads/{id}/assign")
    @RequirePermission("lead:edit")
    public R<Void> assign(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        crmService.assignLead(id, body.get("ownerId") == null ? null : Long.valueOf(String.valueOf(body.get("ownerId"))),
                body.get("storeId") == null ? null : Long.valueOf(String.valueOf(body.get("storeId"))),
                (String) body.get("status"));
        return R.ok();
    }

    @GetMapping("/leads/reminders")
    @RequirePermission("lead:view")
    public R<List<Map<String, Object>>> reminders() {
        return R.ok(crmService.leadReminders());
    }

    @PostMapping("/leads/{id}/convert")
    @RequirePermission("lead:edit")
    public R<Long> convert(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        BigDecimal amount = new BigDecimal(String.valueOf(body.getOrDefault("amount", "0")));
        return R.ok(crmService.convertLead(id, Long.valueOf(String.valueOf(body.get("packageId"))), amount));
    }

    @GetMapping("/members")
    @RequirePermission("member:view")
    public R<PageResult<Map<String, Object>>> members(@RequestParam(required = false) String keyword,
                                                      @RequestParam(required = false) Long levelId,
                                                      @RequestParam(defaultValue = "1") long page,
                                                      @RequestParam(defaultValue = "20") long size) {
        return R.ok(crmService.members(keyword, levelId, page, size));
    }

    @GetMapping("/members/{id}")
    @RequirePermission("member:view")
    public R<Map<String, Object>> member(@PathVariable Long id) {
        return R.ok(crmService.memberDetail(id));
    }

    @PostMapping("/members/{id}/points")
    @RequirePermission("member:edit")
    public R<Void> adjustPoints(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        crmService.adjustPoints(id, Integer.parseInt(String.valueOf(body.get("points"))),
                String.valueOf(body.getOrDefault("reason", "手工调整")));
        return R.ok();
    }

    @PostMapping("/members/{id}/balance")
    @RequirePermission("member:edit")
    public R<Void> adjustBalance(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        crmService.adjustBalance(id, new BigDecimal(String.valueOf(body.get("amount"))),
                String.valueOf(body.getOrDefault("changeType", "ADJUST")),
                String.valueOf(body.getOrDefault("remark", "")));
        return R.ok();
    }

    @PostMapping("/coupons/{id}/grant")
    @RequirePermission("coupon:edit")
    public R<Map<String, Object>> grant(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Long memberId = body.get("memberId") == null ? null : Long.valueOf(String.valueOf(body.get("memberId")));
        int count = Integer.parseInt(String.valueOf(body.getOrDefault("count", "1")));
        return R.ok(crmService.grantCoupon(id, memberId, count));
    }

    @PostMapping("/coupons/redeem")
    @RequirePermission("coupon:edit")
    public R<Map<String, Object>> redeem(@RequestBody Map<String, Object> body) {
        return R.ok(crmService.redeemCoupon(Long.valueOf(String.valueOf(body.get("grantId"))),
                Long.valueOf(String.valueOf(body.get("orderId")))));
    }
}
