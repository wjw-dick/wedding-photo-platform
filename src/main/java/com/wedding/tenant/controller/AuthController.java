package com.wedding.tenant.controller;

import com.wedding.common.api.R;
import com.wedding.tenant.service.TenantAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final TenantAuthService authService;

    @PostMapping("/login")
    public R<Map<String, Object>> login(@RequestBody LoginReq req) {
        return R.ok(authService.login(req.tenantCode(), req.username(), req.password()));
    }

    @GetMapping("/me")
    public R<Map<String, Object>> me() {
        return R.ok(authService.me());
    }

    @PostMapping("/change-password")
    public R<Void> changePassword(@RequestBody ChangePwdReq req) {
        authService.changePassword(req.oldPassword(), req.newPassword());
        return R.ok();
    }

    public record LoginReq(String tenantCode, String username, String password) {
    }

    public record ChangePwdReq(String oldPassword, String newPassword) {
    }
}
