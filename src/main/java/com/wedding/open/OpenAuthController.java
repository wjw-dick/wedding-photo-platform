package com.wedding.open;

import com.wedding.common.api.R;
import com.wedding.platform.entity.PlatformTenant;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/open/{tenantCode}/member")
@RequiredArgsConstructor
public class OpenAuthController {
    private final TenantResolver tenantResolver;
    private final MemberService memberService;

    @PostMapping("/login")
    public R<Map<String, Object>> login(@PathVariable String tenantCode, @RequestBody LoginReq req) {
        PlatformTenant tenant = tenantResolver.byCode(tenantCode);
        return R.ok(memberService.login(tenant, req.unionId(), req.openId(), req.phone(),
                req.nickname(), req.avatar(), req.source()));
    }

    public record LoginReq(String unionId, String openId, String phone, String nickname, String avatar, String source) {
    }
}
