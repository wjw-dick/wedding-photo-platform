package com.wedding.biz.production;

import com.wedding.common.api.R;
import com.wedding.common.audit.Audited;
import com.wedding.common.security.RequirePermission;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tenant/production")
@RequiredArgsConstructor
public class ProductionController {
    private final ProductionService service;

    @GetMapping("/shoot-tasks")
    @RequirePermission("production:view")
    public R<List<Map<String, Object>>> tasks(@RequestParam(required = false) String date,
                                              @RequestParam(required = false) Long employeeId,
                                              @RequestParam(required = false) String status) {
        return R.ok(service.shootTasks(date, employeeId, status));
    }

    @PostMapping("/shoot-tasks/{id}/dispatch")
    @RequirePermission("production:edit")
    @Audited(module = "production", action = "dispatch", targetType = "shootTask")
    public R<Void> dispatch(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        service.dispatch(id, longVal(body.get("photographerId")), longVal(body.get("makeupId")),
                longVal(body.get("assistantId")), (String) body.get("gatherTime"), (String) body.get("gatherPlace"));
        return R.ok();
    }

    @PostMapping("/shoot-tasks/{id}/checkin")
    @RequirePermission("production:edit")
    @Audited(module = "production", action = "checkin", targetType = "shootTask")
    public R<Map<String, Object>> checkin(@PathVariable Long id,
                                          @RequestParam(required = false) BigDecimal lng,
                                          @RequestParam(required = false) BigDecimal lat,
                                          @RequestParam(required = false) MultipartFile photo,
                                          @RequestParam(required = false) String abnormalType,
                                          @RequestParam(required = false) String abnormalDesc) {
        return R.ok(service.checkin(id, lng, lat, photo, abnormalType, abnormalDesc));
    }

    @PostMapping("/shoot-tasks/{id}/finish")
    @RequirePermission("production:edit")
    @Audited(module = "production", action = "finish", targetType = "shootTask")
    public R<Void> finish(@PathVariable Long id) {
        service.finishShoot(id);
        return R.ok();
    }

    @PostMapping("/orders/{orderId}/upload")
    @RequirePermission("production:edit")
    @Audited(module = "production", action = "upload", targetType = "media")
    public R<Map<String, Object>> upload(@PathVariable Long orderId,
                                         @RequestParam(defaultValue = "RAW") String category,
                                         @RequestParam MultipartFile file) {
        return R.ok(service.upload(orderId, category, file));
    }

    @PostMapping("/upload/init")
    @RequirePermission("production:edit")
    public R<Map<String, Object>> init(@RequestBody Map<String, Object> body) {
        return R.ok(service.initUpload((String) body.get("fileName"), longVal(body.get("fileSize")),
                (String) body.get("md5"), intVal(body.get("chunkSize"), 5 * 1024 * 1024), longVal(body.get("orderId"))));
    }

    @PostMapping("/upload/{uploadId}/chunk")
    @RequirePermission("production:edit")
    public R<Void> chunk(@PathVariable String uploadId, @RequestParam int part, @RequestParam MultipartFile chunk) {
        service.uploadChunk(uploadId, part, chunk);
        return R.ok();
    }

    @PostMapping("/upload/{uploadId}/complete")
    @RequirePermission("production:edit")
    @Audited(module = "production", action = "uploadComplete", targetType = "media")
    public R<Map<String, Object>> complete(@PathVariable String uploadId,
                                           @RequestParam(required = false) Long orderId) {
        return R.ok(service.completeUpload(uploadId, orderId));
    }

    @GetMapping("/orders/{orderId}/selection")
    @RequirePermission("selection:view")
    public R<Map<String, Object>> selection(@PathVariable Long orderId) {
        return R.ok(service.selectionSheet(orderId));
    }

    @PostMapping("/orders/{orderId}/selection/submit")
    @RequirePermission("selection:edit")
    @Audited(module = "selection", action = "submit", targetType = "selection")
    public R<Map<String, Object>> submitSelection(@PathVariable Long orderId, @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Number> ids = (List<Number>) body.get("mediaIds");
        return R.ok(service.submitSelection(orderId,
                ids == null ? List.of() : ids.stream().map(Number::longValue).toList(),
                (String) body.get("signImage")));
    }

    @GetMapping("/retouch/pool")
    @RequirePermission("retouch:view")
    public R<List<Map<String, Object>>> pool() {
        return R.ok(service.retouchPool());
    }

    @PostMapping("/retouch/{id}/claim")
    @RequirePermission("retouch:edit")
    @Audited(module = "retouch", action = "claim", targetType = "retouch")
    public R<Void> claim(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        service.claimRetouch(id, longVal(body.get("editorId")));
        return R.ok();
    }

    @PostMapping("/retouch/{id}/submit")
    @RequirePermission("retouch:edit")
    @Audited(module = "retouch", action = "submit", targetType = "retouch")
    public R<Void> submit(@PathVariable Long id) {
        service.submitRetouch(id);
        return R.ok();
    }

    @PostMapping("/retouch/{id}/rework")
    @RequirePermission("retouch:edit")
    @Audited(module = "retouch", action = "rework", targetType = "retouch")
    public R<Void> rework(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        service.reworkRetouch(id, body == null ? "" : body.getOrDefault("reason", ""));
        return R.ok();
    }

    @PostMapping("/retouch/{id}/done")
    @RequirePermission("retouch:edit")
    @Audited(module = "retouch", action = "done", targetType = "retouch")
    public R<Void> done(@PathVariable Long id) {
        service.doneRetouch(id);
        return R.ok();
    }

    @GetMapping("/products")
    @RequirePermission("delivery:view")
    public R<List<Map<String, Object>>> products() {
        return R.ok(service.productOrders());
    }

    @PostMapping("/products/{id}/assign")
    @RequirePermission("delivery:edit")
    @Audited(module = "delivery", action = "assign", targetType = "product")
    public R<Void> assign(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        service.assignProduct(id, longVal(body.get("supplierId")));
        return R.ok();
    }

    @PostMapping("/products/{id}/action")
    @RequirePermission("delivery:edit")
    @Audited(module = "delivery", action = "productAction", targetType = "product")
    public R<Void> productAction(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        service.productAction(id, String.valueOf(body.get("action")),
                (String) body.get("reason"), (String) body.get("trackingCompany"), (String) body.get("trackingNo"));
        return R.ok();
    }

    @GetMapping("/deliveries")
    @RequirePermission("delivery:view")
    public R<List<Map<String, Object>>> deliveries() {
        return R.ok(service.deliveries());
    }

    @PostMapping("/deliveries/{id}/notify")
    @RequirePermission("delivery:edit")
    @Audited(module = "delivery", action = "notify", targetType = "delivery")
    public R<Void> notifyDelivery(@PathVariable Long id, @RequestBody Map<String, String> body) {
        service.notifyDelivery(id, body.getOrDefault("channels", "SITE"));
        return R.ok();
    }

    @PostMapping("/deliveries/{id}/confirm")
    @RequirePermission("delivery:edit")
    @Audited(module = "delivery", action = "confirm", targetType = "delivery")
    public R<Void> confirmDelivery(@PathVariable Long id) {
        service.confirmDelivery(id);
        return R.ok();
    }

    @PostMapping("/deliveries/{id}/extend")
    @RequirePermission("delivery:edit")
    @Audited(module = "delivery", action = "extend", targetType = "delivery")
    public R<Map<String, Object>> extend(@PathVariable Long id) {
        return R.ok(Map.of("retainEnd", service.extendRetention(id)));
    }

    @GetMapping("/dresses")
    @RequirePermission("dress:view")
    public R<List<Map<String, Object>>> dresses() {
        return R.ok(service.dresses());
    }

    @PostMapping("/dresses/{id}/rent")
    @RequirePermission("dress:edit")
    @Audited(module = "dress", action = "rent", targetType = "dress")
    public R<Map<String, Object>> rent(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return R.ok(service.rentDress(id, longVal(body.get("orderId")), longVal(body.get("memberId")),
                LocalDate.parse(String.valueOf(body.get("startDate"))),
                LocalDate.parse(String.valueOf(body.get("endDate")))));
    }

    @PostMapping("/dresses/rentals/{rentalId}/return")
    @RequirePermission("dress:edit")
    @Audited(module = "dress", action = "return", targetType = "dressRental")
    public R<Map<String, Object>> returnDress(@PathVariable Long rentalId, @RequestBody Map<String, Object> body) {
        return R.ok(service.returnDress(rentalId,
                LocalDate.parse(String.valueOf(body.get("returnDate"))),
                body.get("damageFee") == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(body.get("damageFee")))));
    }

    private Long longVal(Object v) {
        return v == null ? null : Long.valueOf(String.valueOf(v));
    }

    private int intVal(Object v, int def) {
        return v == null ? def : Integer.parseInt(String.valueOf(v));
    }
}
