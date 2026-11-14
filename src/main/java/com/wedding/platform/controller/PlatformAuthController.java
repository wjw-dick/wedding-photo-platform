package com.wedding.platform.controller;

import com.wedding.common.api.R;
import com.wedding.platform.service.PlatformAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/platform/auth")
@RequiredArgsConstructor
public class PlatformAuthController {
    private final PlatformAuthService authService;

    @PostMapping("/login")
    public R<Map<String, Object>> login(@RequestBody LoginReq req) {
        return R.ok(authService.login(req.username(), req.password()));
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

    public record LoginReq(String username, String password) {
    }

    public record ChangePwdReq(String oldPassword, String newPassword) {
    }
}
