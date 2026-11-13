package com.wedding.biz.mp;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.wedding.common.api.R;
import com.wedding.common.audit.Audited;
import com.wedding.common.security.RequirePermission;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tenant/mp")
@RequiredArgsConstructor
public class MpController {
    private final MpService mpService;

    @GetMapping("/accounts")
    @RequirePermission("mp:view")
    public R<List<Map<String, Object>>> accounts() {
        return R.ok(mpService.accounts());
    }

    @PostMapping("/accounts")
    @RequirePermission("mp:edit")
    @Audited(module = "mp", action = "saveAccount", targetType = "mpAccount")
    public R<Map<String, Object>> save(@RequestBody Map<String, Object> body) {
        return R.ok(mpService.saveAccount(body));
    }

    @PostMapping("/accounts/{id}/clear-quota")
    @RequirePermission("mp:edit")
    @Audited(module = "mp", action = "clearQuota", targetType = "mpAccount")
    public R<Void> clearQuota(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        if (!Boolean.TRUE.equals(body.get("confirm"))) return R.fail(400, "需二次确认");
        mpService.clearQuota(id);
        return R.ok();
    }

    @PostMapping("/accounts/{id}/sync/tags")
    @RequirePermission("mp:edit")
    @Audited(module = "mp", action = "syncTags", targetType = "mpAccount")
    public R<Map<String, Object>> syncTags(@PathVariable Long id) {
        return R.ok(mpService.syncTags(id));
    }

    @PostMapping("/accounts/{id}/sync/fans")
    @RequirePermission("mp:edit")
    @Audited(module = "mp", action = "syncFans", targetType = "mpAccount")
    public R<Map<String, Object>> syncFans(@PathVariable Long id) {
        return R.ok(mpService.syncFans(id));
    }

    @PostMapping("/accounts/{id}/sync/templates")
    @RequirePermission("mp:edit")
    @Audited(module = "mp", action = "syncTemplates", targetType = "mpAccount")
    public R<Map<String, Object>> syncTemplates(@PathVariable Long id) {
        return R.ok(mpService.syncTemplates(id));
    }

    @PostMapping("/accounts/{id}/publish-menu")
    @RequirePermission("mp:edit")
    @Audited(module = "mp", action = "publishMenu", targetType = "mpMenu")
    public R<Map<String, Object>> publishMenu(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return R.ok(mpService.publishMenu(id, Long.valueOf(String.valueOf(body.get("configId")))));
    }

    @PostMapping("/accounts/{id}/materials")
    @RequirePermission("mp:edit")
    @Audited(module = "mp", action = "uploadMaterial", targetType = "mpMaterial")
    public R<Map<String, Object>> uploadMaterial(@PathVariable Long id,
                                                 @RequestParam(defaultValue = "image") String type,
                                                 @RequestParam MultipartFile file) {
        return R.ok(mpService.uploadMaterial(id, type, file));
    }

    @PostMapping("/drafts/{id}/submit-wx")
    @RequirePermission("mp:edit")
    @Audited(module = "mp", action = "draftSubmit", targetType = "mpDraft")
    public R<Map<String, Object>> submitDraft(@PathVariable Long id) {
        return R.ok(mpService.saveDraftToWx(id));
    }

    @PostMapping("/drafts/{id}/publish")
    @RequirePermission("mp:edit")
    @Audited(module = "mp", action = "publish", targetType = "mpDraft")
    public R<Map<String, Object>> publish(@PathVariable Long id) {
        return R.ok(mpService.publishDraft(id));
    }

    @GetMapping("/qrcode")
    public org.springframework.http.ResponseEntity<byte[]> qrcode(@RequestParam String content) throws Exception {
        QRCodeWriter writer = new QRCodeWriter();
        BitMatrix matrix = writer.encode(content, BarcodeFormat.QR_CODE, 320, 320);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(matrix, "PNG", out);
        return org.springframework.http.ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG).body(out.toByteArray());
    }
}
