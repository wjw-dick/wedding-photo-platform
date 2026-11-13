package com.wedding.biz.report;

import com.wedding.common.api.R;
import com.wedding.common.security.RequirePermission;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/tenant/reports")
@RequiredArgsConstructor
public class ReportController {
    private final ReportService reportService;

    @GetMapping("/overview")
    @RequirePermission("report:view")
    public R<Map<String, Object>> overview(@RequestParam(required = false) String from,
                                           @RequestParam(required = false) String to) {
        return R.ok(reportService.overview(from, to));
    }

    @PostMapping("/tasks")
    @RequirePermission("report:view")
    public R<Map<String, Object>> task(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) body.get("params");
        return R.ok(reportService.createTask(String.valueOf(body.getOrDefault("reportType", "summary")), params));
    }

    @GetMapping("/tasks")
    @RequirePermission("report:view")
    public R<com.wedding.common.api.PageResult<Map<String, Object>>> tasks(@RequestParam(defaultValue = "1") long page,
                                                                           @RequestParam(defaultValue = "20") long size) {
        return R.ok(reportService.tasks(page, size));
    }

    @GetMapping("/tasks/{id}/download")
    @RequirePermission("report:view")
    public R<Map<String, Object>> download(@PathVariable Long id) {
        return R.ok(reportService.download(id));
    }
}
