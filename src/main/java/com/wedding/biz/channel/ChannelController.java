package com.wedding.biz.channel;

import com.wedding.common.api.PageResult;
import com.wedding.common.api.R;
import com.wedding.common.security.RequirePermission;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tenant/channel")
@RequiredArgsConstructor
public class ChannelController {
    private final ChannelService channelService;

    @GetMapping("/channels")
    @RequirePermission("channel:view")
    public R<PageResult<Map<String, Object>>> channels(@RequestParam(required = false) String keyword,
                                                       @RequestParam(required = false) Long parentId,
                                                       @RequestParam(defaultValue = "1") long page,
                                                       @RequestParam(defaultValue = "20") long size) {
        return R.ok(channelService.channels(keyword, parentId, page, size));
    }

    @PostMapping("/channels/{id}/report")
    @RequirePermission("channel:edit")
    public R<Map<String, Object>> report(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return R.ok(channelService.submitReport(id, body));
    }

    @PostMapping("/channels/{id}/account")
    @RequirePermission("channel:edit")
    public R<Map<String, Object>> account(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return R.ok(channelService.createAccount(id, String.valueOf(body.get("username")),
                String.valueOf(body.get("password"))));
    }

    @GetMapping("/reports")
    @RequirePermission("channel:view")
    public R<PageResult<Map<String, Object>>> reports(@RequestParam(required = false) String keyword,
                                                      @RequestParam(required = false) String status,
                                                      @RequestParam(required = false) Long channelId,
                                                      @RequestParam(defaultValue = "1") long page,
                                                      @RequestParam(defaultValue = "20") long size) {
        return R.ok(channelService.reports(keyword, status, channelId, page, size));
    }

    @PostMapping("/reports/{id}/audit")
    @RequirePermission("channel:edit")
    public R<Void> audit(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        channelService.auditReport(id, Boolean.parseBoolean(String.valueOf(body.getOrDefault("approve", true))),
                (String) body.get("remark"));
        return R.ok();
    }

    @PostMapping("/orders/{orderId}/settle")
    @RequirePermission("channel:edit")
    public R<Map<String, Object>> settle(@PathVariable Long orderId) {
        return R.ok(channelService.settleOrder(orderId));
    }

    @GetMapping("/commissions")
    @RequirePermission("channel:view")
    public R<PageResult<Map<String, Object>>> commissions(@RequestParam(required = false) String status,
                                                          @RequestParam(required = false) Long channelId,
                                                          @RequestParam(defaultValue = "1") long page,
                                                          @RequestParam(defaultValue = "20") long size) {
        return R.ok(channelService.commissions(status, channelId, page, size));
    }

    @PostMapping("/settlements")
    @RequirePermission("channel:edit")
    public R<Map<String, Object>> settlement(@RequestBody Map<String, Object> body) {
        return R.ok(channelService.generateSettlement(String.valueOf(body.getOrDefault("targetType", "CHANNEL")),
                Long.valueOf(String.valueOf(body.get("targetId"))), (String) body.get("period")));
    }

    @GetMapping("/settlements")
    @RequirePermission("channel:view")
    public R<List<Map<String, Object>>> settlements(@RequestParam(required = false) String targetType,
                                                    @RequestParam(required = false) Long targetId) {
        return R.ok(channelService.settlements(targetType, targetId));
    }

    @PostMapping("/settlements/{id}/paid")
    @RequirePermission("channel:edit")
    public R<Void> paid(@PathVariable Long id) {
        channelService.paySettlement(id);
        return R.ok();
    }

    @GetMapping("/channels/{id}/sub")
    @RequirePermission("channel:view")
    public R<List<Map<String, Object>>> sub(@PathVariable Long id) {
        return R.ok(channelService.subChannels(id));
    }

    @GetMapping("/channels/{id}/dashboard")
    @RequirePermission("channel:view")
    public R<Map<String, Object>> dashboard(@PathVariable Long id) {
        return R.ok(channelService.dashboard(id));
    }
}
