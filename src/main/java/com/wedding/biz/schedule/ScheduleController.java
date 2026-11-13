package com.wedding.biz.schedule;

import com.wedding.common.api.R;
import com.wedding.common.audit.Audited;
import com.wedding.common.security.RequirePermission;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tenant/schedule")
@RequiredArgsConstructor
public class ScheduleController {
    private final ScheduleService scheduleService;

    @GetMapping("/resources")
    @RequirePermission("schedule:view")
    public R<List<Map<String, Object>>> resources(@RequestParam(required = false) String type) {
        return R.ok(scheduleService.resources(type));
    }

    @GetMapping("/calendar")
    @RequirePermission("schedule:view")
    public R<Map<String, Object>> calendar(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String type) {
        return R.ok(scheduleService.calendar(from, to, type));
    }

    @PostMapping("/slots")
    @RequirePermission("schedule:edit")
    @Audited(module = "schedule", action = "createSlot", targetType = "slot")
    public R<Map<String, Object>> createSlot(@RequestBody Map<String, Object> body) {
        return R.ok(scheduleService.createSlot(
                Long.valueOf(String.valueOf(body.get("resourceId"))),
                LocalDate.parse(String.valueOf(body.get("slotDate"))),
                LocalTime.parse(String.valueOf(body.get("slotStart"))),
                LocalTime.parse(String.valueOf(body.get("slotEnd"))),
                body.get("capacity") == null ? 1 : Integer.valueOf(String.valueOf(body.get("capacity")))));
    }

    @PostMapping("/lock")
    @Audited(module = "schedule", action = "lock", targetType = "slot")
    public R<Map<String, Object>> lock(@RequestBody Map<String, Object> body) {
        return R.ok(scheduleService.lock(
                Long.valueOf(String.valueOf(body.get("resourceId"))),
                LocalDate.parse(String.valueOf(body.get("slotDate"))),
                LocalTime.parse(String.valueOf(body.get("slotStart"))),
                LocalTime.parse(String.valueOf(body.get("slotEnd"))),
                (String) body.get("orderNo"),
                body.get("memberId") == null ? null : Long.valueOf(String.valueOf(body.get("memberId")))));
    }

    @PostMapping("/lock/{lockKey}/release")
    @Audited(module = "schedule", action = "release", targetType = "slot")
    public R<Void> release(@PathVariable String lockKey) {
        scheduleService.release(lockKey);
        return R.ok();
    }
}
