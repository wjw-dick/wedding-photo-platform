package com.wedding.biz.channel;

import com.wedding.common.api.PageResult;
import com.wedding.common.api.R;
import com.wedding.common.context.LoginContext;
import com.wedding.common.context.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/channel-portal")
@RequiredArgsConstructor
public class ChannelPortalController {
    private final ChannelService channelService;
    private final JdbcTemplate jdbc;

    @PostMapping("/auth/login")
    public R<Map<String, Object>> login(@RequestBody Map<String, Object> body) {
        return R.ok(channelService.login(String.valueOf(body.get("tenantCode")),
                String.valueOf(body.get("username")), String.valueOf(body.get("password"))));
    }

    @GetMapping("/dashboard")
    public R<Map<String, Object>> dashboard() {
        return R.ok(channelService.dashboard(LoginContext.require().getId()));
    }

    @PostMapping("/reports")
    public R<Map<String, Object>> report(@RequestBody Map<String, Object> body) {
        return R.ok(channelService.submitReport(LoginContext.require().getId(), body));
    }

    @GetMapping("/reports")
    public R<PageResult<Map<String, Object>>> reports(@RequestParam(required = false) String status,
                                                      @RequestParam(defaultValue = "1") long page,
                                                      @RequestParam(defaultValue = "20") long size) {
        return R.ok(channelService.reports(null, status, LoginContext.require().getId(), page, size));
    }

    @GetMapping("/customers")
    public R<PageResult<Map<String, Object>>> customers(@RequestParam(defaultValue = "1") long page,
                                                        @RequestParam(defaultValue = "20") long size) {
        return R.ok(channelService.reports(null, null, LoginContext.require().getId(), page, size));
    }

    @GetMapping("/commissions")
    public R<PageResult<Map<String, Object>>> commissions(@RequestParam(required = false) String status,
                                                          @RequestParam(defaultValue = "1") long page,
                                                          @RequestParam(defaultValue = "20") long size) {
        return R.ok(channelService.commissions(status, LoginContext.require().getId(), page, size));
    }

    @GetMapping("/settlements")
    public R<List<Map<String, Object>>> settlements() {
        return R.ok(channelService.settlements("CHANNEL", LoginContext.require().getId()));
    }

    @GetMapping("/sub")
    public R<List<Map<String, Object>>> sub() {
        return R.ok(channelService.subChannels(LoginContext.require().getId()));
    }

    @GetMapping("/materials")
    public R<Map<String, Object>> materials() {
        Long tid = TenantContext.requireTenantId();
        Long channelId = LoginContext.require().getId();
        Map<String, Object> channel = jdbc.queryForMap("SELECT * FROM channel WHERE tenant_id=? AND id=?", tid, channelId);
        return R.ok(Map.of(
                "channel", channel,
                "packages", jdbc.queryForList("""
                        SELECT p.*, ROUND(p.price * (1 - ? / 100), 2) AS channel_price
                        FROM pkg_package p WHERE p.tenant_id=? AND p.deleted=0 AND p.status='ON'
                        ORDER BY p.sort_no, p.id
                        """, channel.get("commission_rate"), tid),
                "qrcodes", jdbc.queryForList("SELECT * FROM qr_entry WHERE tenant_id=? AND channel_id=? AND status=1",
                        tid, channelId)));
    }
}
