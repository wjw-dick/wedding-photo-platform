package com.wedding.biz.production;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wedding.common.api.BizException;
import com.wedding.common.context.LoginContext;
import com.wedding.common.context.TenantContext;
import com.wedding.common.security.QuotaService;
import com.wedding.common.storage.StorageManager;
import com.wedding.common.util.BizNo;
import com.wedding.tenant.service.ParamService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ProductionService {
    private final JdbcTemplate jdbc;
    private final StorageManager storageManager;
    private final QuotaService quotaService;
    private final ParamService paramService;
    private final BizNo bizNo;
    private final ObjectMapper objectMapper;

    // ---------------- 拍摄 ----------------

    public List<Map<String, Object>> shootTasks(String date, Long employeeId, String status) {
        Long tid = TenantContext.requireTenantId();
        StringBuilder sql = new StringBuilder("""
                SELECT t.*, o.order_no, o.customer_name, o.customer_phone, o.package_snapshot,
                       s.store_name, s.address AS store_address, s.longitude AS store_lng, s.latitude AS store_lat,
                       e.real_name AS photographer_name, m.real_name AS makeup_name
                FROM shoot_task t
                LEFT JOIN order_main o ON o.id=t.order_id AND o.tenant_id=t.tenant_id
                LEFT JOIN sys_store s ON s.id=t.store_id AND s.tenant_id=t.tenant_id
                LEFT JOIN employee e ON e.id=t.photographer_id AND e.tenant_id=t.tenant_id
                LEFT JOIN employee m ON m.id=t.makeup_id AND m.tenant_id=t.tenant_id
                WHERE t.tenant_id=? AND t.deleted=0
                """);
        List<Object> args = new ArrayList<>();
        args.add(tid);
        if (date != null && !date.isBlank()) {
            sql.append(" AND t.shoot_date=?");
            args.add(date);
        }
        if (employeeId != null) {
            sql.append(" AND (t.photographer_id=? OR t.makeup_id=? OR t.assistant_id=?)");
            args.add(employeeId);
            args.add(employeeId);
            args.add(employeeId);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND t.status=?");
            args.add(status);
        }
        sql.append(" ORDER BY t.shoot_date, t.gather_time");
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    @Transactional
    public void dispatch(Long taskId, Long photographerId, Long makeupId, Long assistantId, String gatherTime, String gatherPlace) {
        Long tid = TenantContext.requireTenantId();
        jdbc.update("""
                UPDATE shoot_task SET photographer_id=?, makeup_id=?, assistant_id=?, gather_time=?, gather_place=?,
                                      status='ASSIGNED'
                WHERE tenant_id=? AND id=?
                """, photographerId, makeupId, assistantId, gatherTime, gatherPlace, tid, taskId);
    }

    /** 现场打卡：GPS 半径可配，超半径拒绝；打卡照片可选。 */
    @Transactional
    public Map<String, Object> checkin(Long taskId, BigDecimal lng, BigDecimal lat, MultipartFile photo,
                                       String abnormalType, String abnormalDesc) {
        Long tid = TenantContext.requireTenantId();
        Map<String, Object> task = jdbc.queryForMap("""
                SELECT t.*, s.longitude AS store_lng, s.latitude AS store_lat
                FROM shoot_task t LEFT JOIN sys_store s ON s.id=t.store_id AND s.tenant_id=t.tenant_id
                WHERE t.tenant_id=? AND t.id=? AND t.deleted=0
                """, tid, taskId);
        int radius = paramService.getInt("shoot.gps.radius", 500);
        int distance = -1;
        if (lng != null && lat != null && task.get("store_lng") != null && task.get("store_lat") != null) {
            distance = (int) Math.round(haversine(
                    ((BigDecimal) task.get("store_lat")).doubleValue(), ((BigDecimal) task.get("store_lng")).doubleValue(),
                    lat.doubleValue(), lng.doubleValue()));
            if (distance > radius) {
                throw new BizException("打卡位置距门店 " + distance + " 米，超过允许半径 " + radius + " 米");
            }
        }
        String photoUrl = null;
        if (photo != null && !photo.isEmpty()) {
            try {
                StorageManager.UploadResult up = storageManager.upload(tid, "CHECKIN",
                        String.valueOf(task.get("order_id")), photo.getOriginalFilename(), photo.getInputStream(),
                        photo.getSize(), photo.getContentType());
                photoUrl = up.objectKey();
            } catch (IOException e) {
                throw new BizException(500, "打卡照片上传失败：" + e.getMessage());
            }
        }
        Long employeeId = LoginContext.get() == null ? null : LoginContext.get().getId();
        jdbc.update("""
                INSERT INTO shoot_checkin(tenant_id, task_id, employee_id, longitude, latitude, distance_m, photo_url,
                                          abnormal_type, abnormal_desc)
                VALUES (?,?,?,?,?,?,?,?,?)
                """, tid, taskId, employeeId, lng, lat, distance, photoUrl, abnormalType, abnormalDesc);
        jdbc.update("UPDATE shoot_task SET status='CHECKED_IN' WHERE tenant_id=? AND id=?", tid, taskId);
        return Map.of("ok", true, "distanceM", distance, "radius", radius,
                "photoUrl", photoUrl == null ? "" : storageManager.signedUrl(tid, storageManager.activeConfig(tid).getProvider(), photoUrl, 30));
    }

    @Transactional
    public void finishShoot(Long taskId) {
        Long tid = TenantContext.requireTenantId();
        Map<String, Object> task = jdbc.queryForMap("SELECT * FROM shoot_task WHERE tenant_id=? AND id=?", tid, taskId);
        jdbc.update("UPDATE shoot_task SET status='DONE' WHERE tenant_id=? AND id=?", tid, taskId);
        Long orderId = ((Number) task.get("order_id")).longValue();
        Map<String, Object> order = jdbc.queryForMap("SELECT status FROM order_main WHERE tenant_id=? AND id=?", tid, orderId);
        if ("SHOOTING".equals(order.get("status"))) {
            jdbc.update("UPDATE order_main SET status='SELECTING' WHERE tenant_id=? AND id=?", tid, orderId);
            jdbc.update("""
                    INSERT INTO order_status_log(tenant_id, order_id, from_status, to_status, action, operator_name)
                    VALUES (?,?, 'SHOOTING', 'SELECTING', 'SHOOT_FINISHED', 'system')
                    """, tid, orderId);
        }
    }

    // ---------------- 上传 ----------------

    @Transactional
    public Map<String, Object> upload(Long orderId, String category, MultipartFile file) {
        Long tid = TenantContext.requireTenantId();
        quotaService.checkStorage(tid, file.getSize());
        Map<String, Object> order = jdbc.queryForMap("SELECT order_no FROM order_main WHERE tenant_id=? AND id=?", tid, orderId);
        try {
            byte[] content = file.getBytes();
            com.wedding.common.util.FileTypeValidator.requireAllowed(
                    file.getOriginalFilename(), file.getContentType(), new java.io.ByteArrayInputStream(content));
            String md5 = md5(content);
            List<Map<String, Object>> exists = jdbc.queryForList(
                    "SELECT * FROM media_file WHERE tenant_id=? AND md5=? LIMIT 1", tid, md5);
            if (!exists.isEmpty()) {
                Map<String, Object> row = exists.get(0);
                return Map.of("instant", true, "objectKey", row.get("object_key"), "provider", row.get("provider"),
                        "url", storageManager.signedUrl(tid, String.valueOf(row.get("provider")),
                                String.valueOf(row.get("object_key")), 30));
            }
            StorageManager.UploadResult up = storageManager.upload(tid, category, String.valueOf(order.get("order_no")),
                    file.getOriginalFilename(), new java.io.ByteArrayInputStream(content), content.length, file.getContentType());
            jdbc.update("UPDATE media_file SET md5=?, order_id=? WHERE tenant_id=? AND object_key=?",
                    md5, orderId, tid, up.objectKey());
            return Map.of("instant", false, "objectKey", up.objectKey(), "provider", up.provider(), "url", up.url(), "md5", md5);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(500, "上传失败：" + e.getMessage());
        }
    }

    public Map<String, Object> initUpload(String fileName, long fileSize, String md5, int chunkSize, Long orderId) {
        Long tid = TenantContext.requireTenantId();
        quotaService.checkStorage(tid, fileSize);
        List<Map<String, Object>> exists = jdbc.queryForList(
                "SELECT * FROM media_file WHERE tenant_id=? AND md5=? LIMIT 1", tid, md5);
        if (!exists.isEmpty()) {
            Map<String, Object> row = exists.get(0);
            return Map.of("instant", true, "objectKey", row.get("object_key"),
                    "url", storageManager.signedUrl(tid, String.valueOf(row.get("provider")),
                            String.valueOf(row.get("object_key")), 30));
        }
        String uploadId = UUID.randomUUID().toString().replace("-", "");
        int totalParts = (int) Math.ceil((double) fileSize / chunkSize);
        jdbc.update("""
                INSERT INTO upload_task(tenant_id, upload_id, order_no, file_name, file_size, md5, chunk_size,
                                        total_parts, status)
                VALUES (?,?,?,?,?,?,?,?, 'UPLOADING')
                """, tid, uploadId, orderId == null ? null : orderNo(orderId), fileName, fileSize, md5, chunkSize, totalParts);
        return Map.of("instant", false, "uploadId", uploadId, "totalParts", totalParts, "chunkSize", chunkSize);
    }

    public void uploadChunk(String uploadId, int part, MultipartFile chunk) {
        Long tid = TenantContext.requireTenantId();
        Map<String, Object> task = jdbc.queryForMap("SELECT * FROM upload_task WHERE tenant_id=? AND upload_id=?",
                tid, uploadId);
        try {
            java.nio.file.Path dir = java.nio.file.Paths.get("data/chunks", String.valueOf(tid), uploadId);
            java.nio.file.Files.createDirectories(dir);
            chunk.transferTo(dir.resolve(String.format("%05d.part", part)).toFile());
            jdbc.update("UPDATE upload_task SET uploaded_parts=uploaded_parts+1 WHERE tenant_id=? AND upload_id=?",
                    tid, uploadId);
        } catch (Exception e) {
            throw new BizException(500, "分片写入失败");
        }
    }

    @Transactional
    public Map<String, Object> completeUpload(String uploadId, Long orderId) {
        Long tid = TenantContext.requireTenantId();
        Map<String, Object> task = jdbc.queryForMap("SELECT * FROM upload_task WHERE tenant_id=? AND upload_id=?",
                tid, uploadId);
        java.nio.file.Path dir = java.nio.file.Paths.get("data/chunks", String.valueOf(tid), uploadId);
        try {
            int parts = ((Number) task.get("total_parts")).intValue();
            java.nio.file.Path assembled = java.nio.file.Paths.get("data/chunks", String.valueOf(tid), uploadId + ".full");
            try (var out = java.nio.file.Files.newOutputStream(assembled)) {
                for (int i = 1; i <= parts; i++) {
                    java.nio.file.Path part = dir.resolve(String.format("%05d.part", i));
                    if (!java.nio.file.Files.exists(part)) throw new BizException("缺少分片 " + i + "，无法合并");
                    java.nio.file.Files.copy(part, out);
                }
            }
            String orderNo = orderId == null ? null : orderNo(orderId);
            String category = String.valueOf(task.getOrDefault("category", "raw")).toUpperCase();
            String md5 = String.valueOf(task.get("md5"));
            String fileName = String.valueOf(task.get("file_name"));
            try (var check = java.nio.file.Files.newInputStream(assembled)) {
                com.wedding.common.util.FileTypeValidator.requireAllowed(
                        fileName, null, check);
            }
            try (var in = java.nio.file.Files.newInputStream(assembled)) {
                StorageManager.UploadResult up = storageManager.upload(tid, category.isEmpty() ? "RAW" : category,
                        orderNo, fileName, in,
                        java.nio.file.Files.size(assembled), "application/octet-stream");
                jdbc.update("UPDATE media_file SET md5=?, order_id=? WHERE tenant_id=? AND object_key=?",
                        md5, orderId, tid, up.objectKey());
                jdbc.update("UPDATE upload_task SET status='DONE', object_key=? WHERE tenant_id=? AND upload_id=?",
                        up.objectKey(), tid, uploadId);
                return Map.of("objectKey", up.objectKey(), "url", up.url(), "provider", up.provider());
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(500, "合并失败：" + e.getMessage());
        }
    }

    // ---------------- 选片 / 修片 ----------------

    public Map<String, Object> selectionSheet(Long orderId) {
        Long tid = TenantContext.requireTenantId();
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM selection_sheet WHERE tenant_id=? AND order_id=? ORDER BY id DESC LIMIT 1", tid, orderId);
        if (rows.isEmpty()) throw BizException.notFound("选片单不存在");
        Map<String, Object> sheet = rows.get(0);
        Map<String, Object> result = new LinkedHashMap<>(sheet);
        result.put("items", jdbc.queryForList(
                "SELECT * FROM selection_item WHERE tenant_id=? AND sheet_id=?", tid, sheet.get("id")));
        return result;
    }

    @Transactional
    public Map<String, Object> submitSelection(Long orderId, List<Long> mediaIds, String signImage) {
        Long tid = TenantContext.requireTenantId();
        Map<String, Object> sheet = jdbc.queryForMap(
                "SELECT * FROM selection_sheet WHERE tenant_id=? AND order_id=? ORDER BY id DESC LIMIT 1 FOR UPDATE", tid, orderId);
        if (!"OPEN".equals(sheet.get("status"))) throw new BizException("选片单已提交或已过期");
        int sellable = ((Number) sheet.get("sellable_count")).intValue();
        BigDecimal extraPrice = (BigDecimal) sheet.get("extra_photo_price");
        int extra = Math.max(0, mediaIds.size() - sellable);
        BigDecimal addonAmount = extraPrice.multiply(BigDecimal.valueOf(extra));
        jdbc.update("DELETE FROM selection_item WHERE tenant_id=? AND sheet_id=?", tid, sheet.get("id"));
        for (int i = 0; i < mediaIds.size(); i++) {
            boolean isExtra = i >= sellable;
            jdbc.update("""
                    INSERT INTO selection_item(tenant_id, sheet_id, media_id, selected, is_extra, price)
                    VALUES (?,?,?,1,?,?)
                    """, tid, sheet.get("id"), mediaIds.get(i), isExtra ? 1 : 0,
                    isExtra ? extraPrice : BigDecimal.ZERO);
        }
        jdbc.update("""
                UPDATE selection_sheet SET selected_count=?, addon_amount=?, sign_image=?, status='CONFIRMED'
                WHERE tenant_id=? AND id=?
                """, mediaIds.size(), addonAmount, signImage, tid, sheet.get("id"));
        jdbc.update("UPDATE order_main SET addon_amount=addon_amount+?, total_amount=total_amount+?, balance_amount=balance_amount+? WHERE tenant_id=? AND id=?",
                addonAmount, addonAmount, addonAmount, tid, orderId);
        if (addonAmount.compareTo(BigDecimal.ZERO) > 0) {
            jdbc.update("""
                    INSERT INTO order_item(tenant_id, order_id, item_type, item_id, item_name, quantity, unit_price, amount)
                    VALUES (?,?, 'ADDON', NULL, '选片加片', ?,?,?)
                    """, tid, orderId, extra, extraPrice, addonAmount);
            jdbc.update("""
                    INSERT INTO pay_record(tenant_id, order_id, order_no, pay_no, pay_type, channel, amount, status,
                                           idempotent_key, paid_at)
                    SELECT ?,?, order_no, ?, 'ADDON', 'OFFLINE', ?, 'PENDING', ?, NULL
                    FROM order_main WHERE tenant_id=? AND id=?
                    """, tid, orderId, bizNo.next("REC"), addonAmount, bizNo.next("IK"), tid, orderId);
        }
        return Map.of("selected", mediaIds.size(), "extra", extra, "addonAmount", addonAmount,
                "status", "CONFIRMED");
    }

    public List<Map<String, Object>> retouchPool() {
        Long tid = TenantContext.requireTenantId();
        return jdbc.queryForList("""
                SELECT r.*, o.order_no, o.customer_name FROM retouch_task r
                LEFT JOIN order_main o ON o.id=r.order_id AND o.tenant_id=r.tenant_id
                WHERE r.tenant_id=? AND r.status IN ('POOL','REWORK') ORDER BY r.id
                """, tid);
    }

    @Transactional
    public void claimRetouch(Long taskId, Long editorId) {
        Long tid = TenantContext.requireTenantId();
        int n = jdbc.update("UPDATE retouch_task SET editor_id=?, status='CLAIMED' WHERE tenant_id=? AND id=? AND status IN ('POOL','REWORK')",
                editorId, tid, taskId);
        if (n == 0) throw new BizException("任务已被领取或状态不可领取");
    }

    @Transactional
    public void submitRetouch(Long taskId) {
        Long tid = TenantContext.requireTenantId();
        jdbc.update("UPDATE retouch_task SET status='SUBMITTED', progress=100 WHERE tenant_id=? AND id=?", tid, taskId);
    }

    @Transactional
    public void reworkRetouch(Long taskId, String reason) {
        Long tid = TenantContext.requireTenantId();
        Map<String, Object> task = jdbc.queryForMap("SELECT * FROM retouch_task WHERE tenant_id=? AND id=?", tid, taskId);
        int count = ((Number) task.get("rework_count")).intValue();
        int limit = ((Number) task.get("rework_limit")).intValue();
        if (count >= limit) throw new BizException("返工次数已达上限 " + limit);
        jdbc.update("UPDATE retouch_task SET status='REWORK', rework_count=rework_count+1 WHERE tenant_id=? AND id=?",
                tid, taskId);
    }

    @Transactional
    public void doneRetouch(Long taskId) {
        Long tid = TenantContext.requireTenantId();
        Map<String, Object> task = jdbc.queryForMap("SELECT * FROM retouch_task WHERE tenant_id=? AND id=?", tid, taskId);
        jdbc.update("UPDATE retouch_task SET status='DONE', progress=100 WHERE tenant_id=? AND id=?", tid, taskId);
        Long orderId = ((Number) task.get("order_id")).longValue();
        jdbc.update("UPDATE order_main SET status='PRODUCING' WHERE tenant_id=? AND id=? AND status='RETOUCHING'", tid, orderId);
        jdbc.update("""
                INSERT INTO order_status_log(tenant_id, order_id, from_status, to_status, action, operator_name)
                VALUES (?,?, 'RETOUCHING', 'PRODUCING', 'RETOUCH_DONE', 'system')
                """, tid, orderId);
    }

    // ---------------- 制作 / 交付 ----------------

    public List<Map<String, Object>> productOrders() {
        Long tid = TenantContext.requireTenantId();
        return jdbc.queryForList("""
                SELECT p.*, o.order_no, o.customer_name, s.supplier_name
                FROM product_order p LEFT JOIN order_main o ON o.id=p.order_id AND o.tenant_id=p.tenant_id
                LEFT JOIN supplier s ON s.id=p.supplier_id AND s.tenant_id=p.tenant_id
                WHERE p.tenant_id=? ORDER BY p.id DESC
                """, tid);
    }

    @Transactional
    public void assignProduct(Long productId, Long supplierId) {
        Long tid = TenantContext.requireTenantId();
        jdbc.update("UPDATE product_order SET supplier_id=?, status='ASSIGNED' WHERE tenant_id=? AND id=?",
                supplierId, tid, productId);
    }

    @Transactional
    public void productAction(Long productId, String action, String reason, String trackingCompany, String trackingNo) {
        Long tid = TenantContext.requireTenantId();
        String status = switch (action) {
            case "accept" -> "ACCEPTED";
            case "reject" -> "REJECTED";
            case "produce" -> "PRODUCING";
            case "ship" -> "SHIPPED";
            case "receive" -> "RECEIVED";
            default -> throw new BizException("非法操作");
        };
        jdbc.update("""
                UPDATE product_order SET status=?, reject_reason=?, tracking_company=?, tracking_no=?
                WHERE tenant_id=? AND id=?
                """, status, reason, trackingCompany, trackingNo, tid, productId);
    }

    public List<Map<String, Object>> deliveries() {
        Long tid = TenantContext.requireTenantId();
        return jdbc.queryForList("""
                SELECT d.*, o.order_no, o.customer_name, o.customer_phone, o.retain_until
                FROM delivery_record d LEFT JOIN order_main o ON o.id=d.order_id AND o.tenant_id=d.tenant_id
                WHERE d.tenant_id=? ORDER BY d.id DESC
                """, tid);
    }

    @Transactional
    public void notifyDelivery(Long deliveryId, String channels) {
        Long tid = TenantContext.requireTenantId();
        jdbc.update("UPDATE delivery_record SET status='NOTIFIED', notified_channels=? WHERE tenant_id=? AND id=?",
                channels, tid, deliveryId);
        jdbc.update("""
                INSERT INTO sys_notice(tenant_id, title, content, channel)
                SELECT ?, '客片交付通知', CONCAT('订单 ', o.order_no, ' 已可交付，请及时取件'), 'SITE'
                FROM delivery_record d JOIN order_main o ON o.id=d.order_id AND o.tenant_id=d.tenant_id
                WHERE d.tenant_id=? AND d.id=?
                """, tid, tid, deliveryId);
    }

    @Transactional
    public void confirmDelivery(Long deliveryId) {
        Long tid = TenantContext.requireTenantId();
        Map<String, Object> d = jdbc.queryForMap("SELECT * FROM delivery_record WHERE tenant_id=? AND id=?", tid, deliveryId);
        jdbc.update("UPDATE delivery_record SET status='DONE', delivered_at=NOW() WHERE tenant_id=? AND id=?", tid, deliveryId);
        jdbc.update("UPDATE order_main SET status='PICKUP' WHERE tenant_id=? AND id=? AND status='PRODUCING'",
                tid, d.get("order_id"));
    }

    @Transactional
    public LocalDate extendRetention(Long deliveryId) {
        Long tid = TenantContext.requireTenantId();
        Map<String, Object> d = jdbc.queryForMap("SELECT * FROM delivery_record WHERE tenant_id=? AND id=?", tid, deliveryId);
        if (((Number) d.get("extended")).intValue() >= 1) throw new BizException("保留期仅可延期一次");
        java.sql.Date end = (java.sql.Date) d.get("retain_end");
        LocalDate newEnd = end.toLocalDate().plusDays(30);
        jdbc.update("UPDATE delivery_record SET retain_end=?, extended=1 WHERE tenant_id=? AND id=?", newEnd, tid, deliveryId);
        jdbc.update("UPDATE order_main SET retain_until=? WHERE tenant_id=? AND id=?", newEnd, tid, d.get("order_id"));
        return newEnd;
    }

    public List<Map<String, Object>> dresses() {
        Long tid = TenantContext.requireTenantId();
        return jdbc.queryForList("SELECT * FROM dress WHERE tenant_id=? AND deleted=0 ORDER BY id", tid);
    }

    @Transactional
    public Map<String, Object> rentDress(Long dressId, Long orderId, Long memberId, LocalDate start, LocalDate end) {
        Long tid = TenantContext.requireTenantId();
        Map<String, Object> dress = jdbc.queryForMap("SELECT * FROM dress WHERE tenant_id=? AND id=? FOR UPDATE", tid, dressId);
        if (!"AVAILABLE".equals(dress.get("status"))) throw new BizException("礼服当前不可租");
        Long conflict = jdbc.queryForObject("""
                SELECT COUNT(*) FROM dress_rental WHERE tenant_id=? AND dress_id=? AND status='RENTED'
                  AND NOT (end_date < ? OR start_date > ?)
                """, Long.class, tid, dressId, start, end);
        if (conflict != null && conflict > 0) throw new BizException("礼服档期冲突");
        long days = java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1;
        BigDecimal rent = ((BigDecimal) dress.get("rent_price")).multiply(BigDecimal.valueOf(days));
        jdbc.update("""
                INSERT INTO dress_rental(tenant_id, dress_id, order_id, member_id, start_date, end_date, deposit,
                                         rent_amount, status)
                VALUES (?,?,?,?,?,?,?,?, 'RENTED')
                """, tid, dressId, orderId, memberId, start, end, dress.get("deposit"), rent);
        jdbc.update("UPDATE dress SET status='RENTED' WHERE tenant_id=? AND id=?", tid, dressId);
        return Map.of("rentAmount", rent, "deposit", dress.get("deposit"), "days", days);
    }

    @Transactional
    public Map<String, Object> returnDress(Long rentalId, LocalDate returnDate, BigDecimal damageFee) {
        Long tid = TenantContext.requireTenantId();
        Map<String, Object> rental = jdbc.queryForMap("SELECT * FROM dress_rental WHERE tenant_id=? AND id=?", tid, rentalId);
        java.sql.Date end = (java.sql.Date) rental.get("end_date");
        long overdueDays = Math.max(0, java.time.temporal.ChronoUnit.DAYS.between(end.toLocalDate(), returnDate));
        BigDecimal overdueFee = BigDecimal.valueOf(overdueDays).multiply(new BigDecimal("50"));
        jdbc.update("""
                UPDATE dress_rental SET status='RETURNED', overdue_fee=?, damage_fee=?, end_date=?
                WHERE tenant_id=? AND id=?
                """, overdueFee, damageFee == null ? BigDecimal.ZERO : damageFee, returnDate, tid, rentalId);
        jdbc.update("UPDATE dress SET status='CLEANING' WHERE tenant_id=? AND id=?", tid, rental.get("dress_id"));
        return Map.of("overdueDays", overdueDays, "overdueFee", overdueFee,
                "damageFee", damageFee == null ? BigDecimal.ZERO : damageFee);
    }

    private String orderNo(Long orderId) {
        return jdbc.queryForObject("SELECT order_no FROM order_main WHERE tenant_id=? AND id=?",
                String.class, TenantContext.requireTenantId(), orderId);
    }

    private String md5(byte[] bytes) throws Exception {
        byte[] digest = MessageDigest.getInstance("MD5").digest(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private double haversine(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
