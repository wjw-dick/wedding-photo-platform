package com.wedding.biz.entry;

import com.wedding.common.api.R;
import com.wedding.common.security.RequirePermission;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tenant/entries")
@RequiredArgsConstructor
public class EntryController {
    private final EntryService entryService;

    @GetMapping
    @RequirePermission("entry:view")
    public R<List<Map<String, Object>>> list() {
        return R.ok(entryService.list());
    }

    @PostMapping
    @RequirePermission("entry:edit")
    public R<Map<String, Object>> save(@RequestBody Map<String, Object> body) {
        return R.ok(entryService.save(body));
    }

    @GetMapping("/{id}/qr.png")
    public ResponseEntity<byte[]> qr(@PathVariable Long id,
                                     @RequestParam(defaultValue = "320") int size) {
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG)
                .body(entryService.qrPng(id, Math.min(Math.max(size, 120), 1024)));
    }

    @GetMapping("/{id}")
    @RequirePermission("entry:view")
    public R<Map<String, Object>> preview(@PathVariable Long id) {
        return R.ok(entryService.preview(id));
    }
}
