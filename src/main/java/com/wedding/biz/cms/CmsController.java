package com.wedding.biz.cms;

import com.wedding.common.api.PageResult;
import com.wedding.common.api.R;
import com.wedding.common.context.TenantContext;
import com.wedding.common.security.RequirePermission;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tenant/cms")
@RequiredArgsConstructor
public class CmsController {
    private final ContentService contentService;

    @GetMapping("/cases")
    @RequirePermission("cms:view")
    public R<PageResult<Map<String, Object>>> cases(@RequestParam(required = false) String keyword,
                                                    @RequestParam(required = false) String status,
                                                    @RequestParam(defaultValue = "1") long page,
                                                    @RequestParam(defaultValue = "20") long size) {
        return R.ok(contentService.cases(keyword, status, page, size));
    }

    @PostMapping("/cases/{id}/publish")
    @RequirePermission("cms:edit")
    public R<Void> publishCase(@PathVariable Long id) {
        contentService.publishCase(id);
        return R.ok();
    }

    @PostMapping("/cases/{id}/revoke")
    @RequirePermission("cms:edit")
    public R<Void> revoke(@PathVariable Long id) {
        contentService.revokeAuth(id);
        return R.ok();
    }

    @PostMapping("/articles/{id}/publish")
    @RequirePermission("cms:edit")
    public R<Void> publishArticle(@PathVariable Long id) {
        contentService.publishArticle(id);
        return R.ok();
    }

    @GetMapping("/lead-submits")
    @RequirePermission("lead:view")
    public R<PageResult<Map<String, Object>>> submits(@RequestParam(required = false) String status,
                                                      @RequestParam(defaultValue = "1") long page,
                                                      @RequestParam(defaultValue = "20") long size) {
        return R.ok(contentService.leadSubmits(status, page, size));
    }

    @PostMapping("/lead-submits/{id}/convert")
    @RequirePermission("lead:edit")
    public R<Long> convert(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return R.ok(contentService.convertSubmit(id,
                body.get("storeId") == null ? null : Long.valueOf(String.valueOf(body.get("storeId"))),
                body.get("ownerId") == null ? null : Long.valueOf(String.valueOf(body.get("ownerId")))));
    }

    @GetMapping("/notices")
    @RequirePermission("system:view")
    public R<List<Map<String, Object>>> notices(@RequestParam(defaultValue = "false") boolean unreadOnly) {
        return R.ok(contentService.notices(unreadOnly));
    }

    @PostMapping("/notices/{id}/read")
    public R<Void> read(@PathVariable Long id) {
        contentService.readNotice(id);
        return R.ok();
    }

    @PostMapping("/message/test")
    @RequirePermission("system:edit")
    public R<Map<String, Object>> test(@RequestBody Map<String, Object> body) {
        return R.ok(contentService.testChannel((String) body.get("channel"), (String) body.get("title"),
                (String) body.get("content")));
    }

    @PostMapping("/live-albums")
    @RequirePermission("production:edit")
    public R<Map<String, Object>> createAlbum(@RequestBody Map<String, Object> body) {
        return R.ok(contentService.createAlbum(Long.valueOf(String.valueOf(body.get("orderId"))),
                (String) body.get("name"), (String) body.get("startAt"), (String) body.get("endAt")));
    }

    @GetMapping("/live-albums/{token}")
    public R<Map<String, Object>> album(@PathVariable String token) {
        return R.ok(contentService.album(TenantContext.requireTenantId(), token));
    }
}
