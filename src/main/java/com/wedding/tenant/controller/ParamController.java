package com.wedding.tenant.controller;

import com.wedding.common.api.R;
import com.wedding.common.audit.Audited;
import com.wedding.common.security.RequirePermission;
import com.wedding.tenant.service.ParamService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tenant/params")
@RequiredArgsConstructor
public class ParamController {
    private final ParamService paramService;

    @GetMapping
    @RequirePermission("system:view")
    public R<Map<String, List<Map<String, Object>>>> grouped() {
        return R.ok(paramService.grouped());
    }

    @GetMapping("/flat")
    public R<Map<String, String>> flat() {
        return R.ok(paramService.all());
    }

    @PutMapping
    @RequirePermission("system:edit")
    @Audited(module = "param", action = "save", targetType = "param")
    public R<Void> save(@RequestBody Map<String, String> params) {
        paramService.save(params);
        return R.ok();
    }
}
