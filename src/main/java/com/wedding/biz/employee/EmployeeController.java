package com.wedding.biz.employee;

import com.wedding.common.api.PageResult;
import com.wedding.common.api.R;
import com.wedding.common.security.RequirePermission;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tenant/employee")
@RequiredArgsConstructor
public class EmployeeController {
    private final EmployeeService employeeService;

    @GetMapping
    @RequirePermission("employee:view")
    public R<PageResult<Map<String, Object>>> employees(@RequestParam(required = false) String keyword,
                                                        @RequestParam(required = false) String roleType,
                                                        @RequestParam(defaultValue = "1") long page,
                                                        @RequestParam(defaultValue = "20") long size) {
        return R.ok(employeeService.employees(keyword, roleType, page, size));
    }

    @GetMapping("/schedules")
    @RequirePermission("employee:view")
    public R<List<Map<String, Object>>> schedules(@RequestParam(required = false) String from,
                                                  @RequestParam(required = false) String to,
                                                  @RequestParam(required = false) Long storeId) {
        return R.ok(employeeService.schedules(from, to, storeId));
    }

    @PostMapping("/schedules")
    @RequirePermission("employee:edit")
    public R<Void> schedule(@RequestBody Map<String, Object> body) {
        employeeService.saveSchedule(Long.valueOf(String.valueOf(body.get("employeeId"))),
                String.valueOf(body.get("workDate")), (String) body.get("shift"),
                body.get("storeId") == null ? null : Long.valueOf(String.valueOf(body.get("storeId"))),
                body.get("status") == null ? null : Integer.valueOf(String.valueOf(body.get("status"))));
        return R.ok();
    }

    @GetMapping("/rules")
    @RequirePermission("employee:view")
    public R<List<Map<String, Object>>> rules() {
        return R.ok(employeeService.rules());
    }

    @PostMapping("/rules")
    @RequirePermission("employee:edit")
    public R<Long> rule(@RequestBody Map<String, Object> body) {
        return R.ok(employeeService.saveRule(body));
    }

    @PostMapping("/orders/{orderId}/commission")
    @RequirePermission("employee:edit")
    public R<Map<String, Object>> commission(@PathVariable Long orderId) {
        return R.ok(employeeService.calculateForOrder(orderId));
    }

    @GetMapping("/commissions")
    @RequirePermission("employee:view")
    public R<PageResult<Map<String, Object>>> commissions(@RequestParam(required = false) String status,
                                                          @RequestParam(required = false) Long employeeId,
                                                          @RequestParam(defaultValue = "1") long page,
                                                          @RequestParam(defaultValue = "20") long size) {
        return R.ok(employeeService.commissionDetails(status, employeeId, page, size));
    }

    @GetMapping("/performance")
    @RequirePermission("employee:view")
    public R<Map<String, Object>> performance(@RequestParam(required = false) String from,
                                              @RequestParam(required = false) String to) {
        return R.ok(employeeService.performance(from, to));
    }

    @GetMapping("/my/tasks")
    public R<Map<String, Object>> myTasks() {
        return R.ok(employeeService.myTasks());
    }
}
