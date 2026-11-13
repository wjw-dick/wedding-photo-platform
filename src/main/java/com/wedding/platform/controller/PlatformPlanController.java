package com.wedding.platform.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wedding.common.api.R;
import com.wedding.common.audit.Audited;
import com.wedding.common.security.RequirePermission;
import com.wedding.platform.entity.PlatformPlan;
import com.wedding.platform.mapper.PlatformPlanMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/platform/plans")
@RequiredArgsConstructor
public class PlatformPlanController {
    private final PlatformPlanMapper planMapper;

    @GetMapping
    @RequirePermission("plan:read")
    public R<List<PlatformPlan>> list() {
        return R.ok(planMapper.selectList(new LambdaQueryWrapper<PlatformPlan>()
                .orderByAsc(PlatformPlan::getPrice)));
    }

    @PostMapping
    @RequirePermission("plan:create")
    @Audited(module = "plan", action = "create", targetType = "plan")
    public R<PlatformPlan> create(@RequestBody PlatformPlan plan) {
        plan.setPlanId(null);
        plan.setStatus(plan.getStatus() == null ? "ACTIVE" : plan.getStatus());
        planMapper.insert(plan);
        return R.ok(plan);
    }

    @PutMapping("/{id}")
    @RequirePermission("plan:update")
    @Audited(module = "plan", action = "update", targetType = "plan")
    public R<PlatformPlan> update(@PathVariable Long id, @RequestBody PlatformPlan plan) {
        plan.setPlanId(id);
        planMapper.updateById(plan);
        return R.ok(plan);
    }

    @DeleteMapping("/{id}")
    @RequirePermission("plan:delete")
    @Audited(module = "plan", action = "delete", targetType = "plan")
    public R<Void> delete(@PathVariable Long id) {
        planMapper.deleteById(id);
        return R.ok();
    }
}
