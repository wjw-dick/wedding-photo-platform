package com.wedding.open;

import com.wedding.common.api.R;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 租户 CAS 2.0 单点登录入口，挂在免鉴权的 /api/public/** 下。
 * - /config    登录页据此决定是否显示统一认证按钮
 * - /login     302 跳转到 CAS 服务器登录页（携带 service）
 * - /callback  CAS 回跳，校验 ticket 后签发 JWT 并 302 回前端
 */
@RestController
@RequestMapping("/api/public/{tenantCode}/sso")
@RequiredArgsConstructor
public class CasSsoController {
    private final CasSsoService casSsoService;

    @GetMapping("/config")
    public R<Map<String, Object>> config(@PathVariable String tenantCode) {
        return R.ok(casSsoService.config(tenantCode));
    }

    @GetMapping("/login")
    public ResponseEntity<Void> login(@PathVariable String tenantCode) {
        return redirect(casSsoService.loginUrl(tenantCode));
    }

    @GetMapping("/callback")
    public ResponseEntity<Void> callback(@PathVariable String tenantCode,
                                         @RequestParam(required = false) String ticket) {
        return redirect(casSsoService.callback(tenantCode, ticket));
    }

    private ResponseEntity<Void> redirect(String location) {
        return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, location).build();
    }
}
