package com.wedding.tenant.controller;

import com.wedding.common.api.PageResult;
import com.wedding.common.api.R;
import com.wedding.tenant.service.GenericCrudService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 标准业务模块 CRUD 通道。复杂流程使用各自专用接口。 */
@RestController
@RequestMapping("/api/tenant/crud/{table}")
@RequiredArgsConstructor
public class GenericCrudController {
    private final GenericCrudService crudService;

    @GetMapping
    public R<PageResult<Map<String, Object>>> list(@PathVariable String table,
                                                   @RequestParam Map<String, String> params,
                                                   @RequestParam(defaultValue = "1") long page,
                                                   @RequestParam(defaultValue = "20") long size) {
        return R.ok(crudService.list(table, params, page, size));
    }

    @GetMapping("/{id}")
    public R<Map<String, Object>> get(@PathVariable String table, @PathVariable Long id) {
        return R.ok(crudService.get(table, id));
    }

    @PostMapping
    public R<Map<String, Object>> create(@PathVariable String table, @RequestBody Map<String, Object> body) {
        return R.ok(Map.of("id", crudService.create(table, body)));
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable String table, @PathVariable Long id,
                          @RequestBody Map<String, Object> body) {
        crudService.update(table, id, body);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable String table, @PathVariable Long id) {
        crudService.delete(table, id);
        return R.ok();
    }
}
