package com.wedding.biz.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wedding.common.api.BizException;
import com.wedding.common.context.TenantContext;
import com.wedding.common.security.TenantDataGuard;
import com.wedding.common.util.BizNo;
import com.wedding.tenant.service.ParamService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class OrderService {
    private static final Map<String, List<String>> ALLOWED = Map.ofEntries(
            Map.entry("LEAD", List.of("ARRIVED", "CANCELLED")),
            Map.entry("ARRIVED", List.of("DEPOSIT", "CANCELLED")),
            Map.entry("DEPOSIT", List.of("SCHEDULED", "CANCELLED")),
            Map.entry("SCHEDULED", List.of("SHOOTING", "CANCELLED")),
            Map.entry("SHOOTING", List.of("SELECTING")),
            Map.entry("SELECTING", List.of("RETOUCHING")),
            Map.entry("RETOUCHING", List.of("PRODUCING", "RETOUCHING")),
            Map.entry("PRODUCING", List.of("PICKUP", "RETOUCHING")),
            Map.entry("PICKUP", List.of("COMPLETED")),
            Map.entry("COMPLETED", List.of())
    );
    private final JdbcTemplate jdbc;
    private final TenantDataGuard tenantDataGuard;
    private final ObjectMapper objectMapper;
    private final BizNo bizNo;
    private final ParamService paramService;
    private final com.wedding.biz.schedule.ScheduleService scheduleService;
    private final RabbitTemplate rabbitTemplate;

    @Transactional
    public Map<String, Object> create(CreateOrderReq req) {
        Long tid = TenantContext.requireTenantId();
        Map<String, Object> pkg;
        try {
            pkg = jdbc.queryForMap("SELECT * FROM pkg_package WHERE tenant_id=? AND id=? AND deleted=0", tid, req.packageId());
        } catch (Exception e) {
            throw new BizException("套系不存在");
        }
        if (!"ON".equals(pkg.get("status"))) throw new BizException("套系未上架");
        BigDecimal packagePrice = (BigDecimal) pkg.get("price");
        List<Map<String, Object>> items = new ArrayList<>();
        BigDecimal addonAmount = BigDecimal.ZERO;
        if (req.addons() != null) {
            for (AddonReq a : req.addons()) {
                Map<String, Object> addon = jdbc.queryForMap(
                        "SELECT * FROM pkg_addon WHERE tenant_id=? AND id=? AND deleted=0", tid, a.addonId());
                BigDecimal price = addonPrice(tid, a.addonId(), req.packageId(), req.storeId());
                BigDecimal amount = price.multiply(BigDecimal.valueOf(a.quantity()));
                addonAmount = addonAmount.add(amount);
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("itemType", "ADDON");
                item.put("itemId", a.addonId());
                item.put("itemName", addon.get("addon_name"));
                item.put("quantity", a.quantity());
                item.put("unitPrice", price);
                item.put("amount", amount);
                item.put("snapshot", json(addon));
                items.add(item);
            }
        }
        BigDecimal total = packagePrice.add(addonAmount);
        int depositRatio = paramService.getInt("deposit.ratio", 30);
        BigDecimal deposit = total.multiply(BigDecimal.valueOf(depositRatio))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        String orderNo = bizNo.orderNo();

        Map<String, Object> packageItem = new LinkedHashMap<>();
        packageItem.put("itemType", "PACKAGE");
        packageItem.put("itemId", req.packageId());
        packageItem.put("itemName", pkg.get("name"));
        packageItem.put("quantity", 1);
        packageItem.put("unitPrice", packagePrice);
        packageItem.put("amount", packagePrice);
        packageItem.put("snapshot", json(pkg));
        items.add(0, packageItem);

        String commissionSnapshot = buildCommissionSnapshot(tid, req);
        jdbc.update("""
                INSERT INTO order_main(tenant_id, order_no, store_id, member_id, customer_name, customer_phone,
                    package_id, package_snapshot, channel_id, salesman_id, shoot_date, status, total_amount,
                    addon_amount, paid_amount, refund_amount, deposit_amount, balance_amount,
                    commission_snapshot, source, remark)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,0,0,?,?,?,?,?)
                """, tid, orderNo, req.storeId(), req.memberId(), req.customerName(), req.customerPhone(),
                req.packageId(), json(pkg), req.channelId(), req.salesmanId(),
                req.shootDate(), "LEAD", total, addonAmount, deposit, total.subtract(deposit),
                commissionSnapshot, req.source() == null ? "STORE" : req.source(), req.remark());
        Long orderId = jdbc.queryForObject("SELECT id FROM order_main WHERE tenant_id=? AND order_no=?", Long.class, tid, orderNo);

        for (Map<String, Object> item : items) {
            jdbc.update("""
                    INSERT INTO order_item(tenant_id, order_id, item_type, item_id, item_name, quantity, unit_price, amount, snapshot)
                    VALUES (?,?,?,?,?,?,?,?,?)
                    """, tid, orderId, item.get("itemType"), item.get("itemId"), item.get("itemName"),
                    item.get("quantity"), item.get("unitPrice"), item.get("amount"), item.get("snapshot"));
        }
        logStatus(tid, orderId, null, "LEAD", "CREATE", null, req.remark());

        List<Map<String, Object>> locks = new ArrayList<>();
        if (req.shootDate() != null && req.resourceIds() != null) {
            for (Long resourceId : req.resourceIds()) {
                locks.add(scheduleService.lock(resourceId, req.shootDate(), req.shootStart(), req.shootEnd(),
                        orderNo, req.memberId()));
            }
        }
        saveCommission(tid, orderId, req, total);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderId", orderId);
        result.put("orderNo", orderNo);
        result.put("totalAmount", total);
        result.put("depositAmount", deposit);
        result.put("balanceAmount", total.subtract(deposit));
        result.put("locks", locks);
        return result;
    }

    public com.wedding.common.api.PageResult<Map<String, Object>> list(String keyword, String status, Long storeId,
                                          String from, String to, long page, long size) {
        Long tid = TenantContext.requireTenantId();
        StringBuilder where = new StringBuilder(" WHERE o.tenant_id=? AND o.deleted=0");
        List<Object> args = new ArrayList<>();
        args.add(tid);
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (o.order_no LIKE ? OR o.customer_name LIKE ? OR o.customer_phone LIKE ?)");
            for (int i = 0; i < 3; i++) args.add("%" + keyword + "%");
        }
        if (status != null && !status.isBlank()) {
            where.append(" AND o.status=?");
            args.add(status);
        }
        if (storeId != null) {
            where.append(" AND o.store_id=?");
            args.add(storeId);
        }
        if (from != null && !from.isBlank()) {
            where.append(" AND o.created_at>=?");
            args.add(from + " 00:00:00");
        }
        if (to != null && !to.isBlank()) {
            where.append(" AND o.created_at<=?");
            args.add(to + " 23:59:59");
        }
        com.wedding.common.context.LoginContext.LoginUser u = com.wedding.common.context.LoginContext.get();
        if (u != null && "STORE".equals(u.getDataScope()) && u.getStoreId() != null && u.getStoreId() > 0) {
            where.append(" AND o.store_id=?");
            args.add(u.getStoreId());
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM order_main o" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add((page - 1) * size);
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT o.*, s.store_name, p.name AS package_name
                FROM order_main o LEFT JOIN sys_store s ON s.id=o.store_id AND s.tenant_id=o.tenant_id
                LEFT JOIN pkg_package p ON p.id=o.package_id AND p.tenant_id=o.tenant_id
                """ + where + " ORDER BY o.id DESC LIMIT ? OFFSET ?", pageArgs.toArray());
        return com.wedding.common.api.PageResult.of(total == null ? 0 : total, page, size, rows);
    }

    public Map<String, Object> detail(Long orderId) {
        Long tid = TenantContext.requireTenantId();
        tenantDataGuard.requireOwned("order_main", orderId, "订单");
        Map<String, Object> order = jdbc.queryForMap("""
                SELECT o.*, s.store_name, p.name AS package_name
                FROM order_main o LEFT JOIN sys_store s ON s.id=o.store_id AND s.tenant_id=o.tenant_id
                LEFT JOIN pkg_package p ON p.id=o.package_id AND p.tenant_id=o.tenant_id
                WHERE o.tenant_id=? AND o.id=? AND o.deleted=0
                """, tid, orderId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("order", order);
        result.put("items", jdbc.queryForList("SELECT * FROM order_item WHERE tenant_id=? AND order_id=?", tid, orderId));
        result.put("statusLogs", jdbc.queryForList("SELECT * FROM order_status_log WHERE tenant_id=? AND order_id=? ORDER BY id", tid, orderId));
        result.put("payments", jdbc.queryForList("SELECT * FROM pay_record WHERE tenant_id=? AND order_id=? ORDER BY id", tid, orderId));
        result.put("refunds", jdbc.queryForList("SELECT * FROM refund_record WHERE tenant_id=? AND order_id=? ORDER BY id", tid, orderId));
        result.put("contracts", jdbc.queryForList("SELECT * FROM order_contract_sign WHERE tenant_id=? AND order_id=? ORDER BY id", tid, orderId));
        return result;
    }

    @Transactional
    public void transition(Long orderId, String to, String remark) {
        Long tid = TenantContext.requireTenantId();
        tenantDataGuard.requireOwned("order_main", orderId, "订单");
        Map<String, Object> order = jdbc.queryForMap(
                "SELECT * FROM order_main WHERE tenant_id=? AND id=? AND deleted=0 FOR UPDATE", tid, orderId);
        String from = String.valueOf(order.get("status"));
        if (from.equals(to)) return;
        List<String> allowed = ALLOWED.getOrDefault(from, List.of());
        if (!allowed.contains(to)) {
            throw new BizException("订单状态不允许从 " + from + " 流转到 " + to);
        }
        jdbc.update("UPDATE order_main SET status=? WHERE tenant_id=? AND id=?", to, tid, orderId);
        logStatus(tid, orderId, from, to, "TRANSITION", null, remark);
        sideEffects(tid, order, to);
        publish("ORDER_STATUS", orderId, order.get("order_no"), Map.of("from", from, "to", to));
    }

    private void sideEffects(Long tid, Map<String, Object> order, String to) {
        Long orderId = ((Number) order.get("id")).longValue();
        String orderNo = String.valueOf(order.get("order_no"));
        switch (to) {
            case "SHOOTING" -> {
                Long exists = jdbc.queryForObject("SELECT COUNT(*) FROM shoot_task WHERE tenant_id=? AND order_id=? AND deleted=0",
                        Long.class, tid, orderId);
                if (exists == null || exists == 0) {
                    jdbc.update("""
                            INSERT INTO shoot_task(tenant_id, order_id, store_id, task_no, shoot_date, status)
                            VALUES (?,?,?,?,?, 'ASSIGNED')
                            """, tid, orderId, order.get("store_id"), bizNo.next("ST"), order.get("shoot_date"));
                }
            }
            case "SELECTING" -> {
                Long exists = jdbc.queryForObject("SELECT COUNT(*) FROM selection_sheet WHERE tenant_id=? AND order_id=?",
                        Long.class, tid, orderId);
                if (exists == null || exists == 0) {
                    int sellable = 120;
                    try {
                        Map<String, Object> pkg = objectMapper.readValue(String.valueOf(order.get("package_snapshot")), Map.class);
                        if (pkg.get("finished_count") instanceof Number n) sellable = n.intValue();
                    } catch (Exception ignored) {
                    }
                    jdbc.update("""
                            INSERT INTO selection_sheet(tenant_id, order_id, sheet_no, sellable_count, extra_photo_price,
                                                        deadline_at, status)
                            VALUES (?,?,?,?,?,?, 'OPEN')
                            """, tid, orderId, bizNo.next("SS"), sellable, new BigDecimal("50"),
                            LocalDateTime.now().plusDays(7));
                }
            }
            case "RETOUCHING" -> {
                Long exists = jdbc.queryForObject("SELECT COUNT(*) FROM retouch_task WHERE tenant_id=? AND order_id=?",
                        Long.class, tid, orderId);
                if (exists == null || exists == 0) {
                    jdbc.update("""
                            INSERT INTO retouch_task(tenant_id, order_id, task_no, spec, item_count, deadline_at, status)
                            VALUES (?,?,?,?,?,?, 'POOL')
                            """, tid, orderId, bizNo.next("RT"), "调色/磨皮/瘦身/合成", 30,
                            LocalDateTime.now().plusDays(10));
                }
            }
            case "PRODUCING" -> {
                Long exists = jdbc.queryForObject("SELECT COUNT(*) FROM product_order WHERE tenant_id=? AND order_id=?",
                        Long.class, tid, orderId);
                if (exists == null || exists == 0) {
                    jdbc.update("""
                            INSERT INTO product_order(tenant_id, order_id, product_no, product_type, spec, status)
                            VALUES (?,?,?,?,?, 'CREATED')
                            """, tid, orderId, bizNo.next("PD"), "ALBUM", "按套系规格自动生成");
                }
            }
            case "PICKUP" -> {
                Long exists = jdbc.queryForObject("SELECT COUNT(*) FROM delivery_record WHERE tenant_id=? AND order_id=?",
                        Long.class, tid, orderId);
                if (exists == null || exists == 0) {
                    int days = paramService.getInt("retain.days", 90);
                    jdbc.update("""
                            INSERT INTO delivery_record(tenant_id, order_id, retain_start, retain_end, status)
                            VALUES (?,?,?,?, 'NOTIFIED')
                            """, tid, orderId, LocalDate.now(), LocalDate.now().plusDays(days));
                    jdbc.update("UPDATE order_main SET retain_until=? WHERE tenant_id=? AND id=?",
                            LocalDate.now().plusDays(days), tid, orderId);
                }
            }
            case "COMPLETED" -> jdbc.update(
                    "UPDATE commission_record SET status='SETTLED' WHERE tenant_id=? AND order_id=?", tid, orderId);
            default -> {
            }
        }
    }

    @Transactional
    public Map<String, Object> pay(String orderNo, String payNo, String payType, String channel,
                                   BigDecimal amount, String raw, String operator) {
        Long tid = TenantContext.requireTenantId();
        Map<String, Object> order = jdbc.queryForMap(
                "SELECT * FROM order_main WHERE tenant_id=? AND order_no=? AND deleted=0 FOR UPDATE", tid, orderNo);
        String idempotent = tid + ":" + orderNo + ":" + payNo + ":" + amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
        try {
            jdbc.update("""
                    INSERT INTO pay_record(tenant_id, order_id, order_no, pay_no, pay_type, channel, amount, status,
                                           idempotent_key, callback_raw, paid_at)
                    VALUES (?,?,?,?,?,?,?, 'SUCCESS', ?,?,NOW())
                    """, tid, order.get("id"), orderNo, payNo, payType, channel, amount, idempotent, raw);
        } catch (DuplicateKeyException e) {
            return Map.of("duplicate", true, "message", "重复回调已忽略");
        }
        BigDecimal paid = ((BigDecimal) order.get("paid_amount")).add(amount);
        BigDecimal addon = (BigDecimal) order.get("addon_amount");
        String newStatus = String.valueOf(order.get("status"));
        if ("DEPOSIT".equals(payType)) {
            if ("LEAD".equals(newStatus)) newStatus = "ARRIVED";
            if ("ARRIVED".equals(newStatus)) newStatus = "DEPOSIT";
            if (order.get("shoot_date") != null) {
                List<Map<String, Object>> locks = jdbc.queryForList(
                        "SELECT * FROM sch_lock WHERE tenant_id=? AND order_no=? AND status='LOCKED'", tid, orderNo);
                for (Map<String, Object> lock : locks) {
                    scheduleService.confirm(String.valueOf(lock.get("lock_key")), ((Number) order.get("id")).longValue());
                }
                newStatus = "SCHEDULED";
                sideEffects(tid, order, "SHOOTING");
            }
        }
        if ("ADDON".equals(payType)) addon = addon.add(amount);
        jdbc.update("UPDATE order_main SET paid_amount=?, addon_amount=?, status=? WHERE tenant_id=? AND id=?",
                paid, addon, newStatus, tid, order.get("id"));
        logStatus(tid, ((Number) order.get("id")).longValue(), String.valueOf(order.get("status")), newStatus,
                "PAY_" + payType, operator, "支付 " + amount);
        publish("ORDER_PAID", ((Number) order.get("id")).longValue(), orderNo,
                Map.of("payType", payType, "amount", amount, "status", newStatus));
        return Map.of("duplicate", false, "status", newStatus, "paidAmount", paid);
    }

    @Transactional
    public Map<String, Object> applyRefund(Long orderId, BigDecimal amount, String reason) {
        Long tid = TenantContext.requireTenantId();
        String refundNo = bizNo.next("RF");
        jdbc.update("""
                INSERT INTO refund_record(tenant_id, order_id, refund_no, amount, reason, status)
                VALUES (?,?,?,?,?, 'PENDING')
                """, tid, orderId, refundNo, amount, reason);
        return Map.of("refundNo", refundNo, "status", "PENDING");
    }

    @Transactional
    public void auditRefund(Long refundId, boolean approve, String remark) {
        Long tid = TenantContext.requireTenantId();
        Map<String, Object> refund = jdbc.queryForMap(
                "SELECT * FROM refund_record WHERE tenant_id=? AND id=? FOR UPDATE", tid, refundId);
        if (!"PENDING".equals(refund.get("status"))) throw new BizException("退款单已处理");
        String status = approve ? "DONE" : "REJECTED";
        jdbc.update("UPDATE refund_record SET status=?, audit_remark=?, audit_user_id=? WHERE tenant_id=? AND id=?",
                status, remark, com.wedding.common.context.LoginContext.require().getId(), tid, refundId);
        if (approve) {
            jdbc.update("UPDATE order_main SET refund_amount=refund_amount+? WHERE tenant_id=? AND id=?",
                    refund.get("amount"), tid, refund.get("order_id"));
            Map<String, Object> order = jdbc.queryForMap("SELECT * FROM order_main WHERE tenant_id=? AND id=?",
                    tid, refund.get("order_id"));
            if (((BigDecimal) order.get("refund_amount")).compareTo((BigDecimal) order.get("paid_amount")) >= 0) {
                jdbc.update("UPDATE order_main SET status='CANCELLED', abnormal_status='CANCELLED' WHERE tenant_id=? AND id=?",
                        tid, order.get("id"));
            }
        }
    }

    @Transactional
    public void signContract(Long orderId, Long contractId, String signImage, String pdfUrl) {
        Long tid = TenantContext.requireTenantId();
        String snapshot = null;
        if (contractId != null) {
            snapshot = jdbc.queryForObject("SELECT content FROM order_contract WHERE tenant_id=? AND id=?",
                    String.class, tid, contractId);
        }
        jdbc.update("""
                INSERT INTO order_contract_sign(tenant_id, order_id, contract_id, contract_snapshot, sign_image, signed_at, pdf_url)
                VALUES (?,?,?,?,?,NOW(),?)
                """, tid, orderId, contractId, snapshot, signImage, pdfUrl);
    }

    private void saveCommission(Long tid, Long orderId, CreateOrderReq req, BigDecimal total) {
        if (req.channelId() != null) {
            Map<String, Object> channel = jdbc.queryForMap("SELECT * FROM channel WHERE tenant_id=? AND id=?", tid, req.channelId());
            BigDecimal rate = (BigDecimal) channel.get("commission_rate");
            BigDecimal amount = total.multiply(rate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            jdbc.update("""
                    INSERT INTO commission_record(tenant_id, order_id, channel_id, commission_type, rule_snapshot,
                                                  base_amount, rate, amount, status)
                    VALUES (?,?,?, 'CHANNEL', ?,?,?,?, 'PENDING')
                    """, tid, orderId, req.channelId(), "渠道佣金比例 " + rate + "%", total, rate, amount);
        }
        if (req.salesmanId() != null) {
            List<Map<String, Object>> rules = jdbc.queryForList(
                    "SELECT * FROM commission_rule WHERE tenant_id=? AND rule_type='SALES' AND status=1 LIMIT 1", tid);
            BigDecimal rate = rules.isEmpty() ? new BigDecimal("2") : (BigDecimal) rules.get(0).get("rate");
            BigDecimal amount = total.multiply(rate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            jdbc.update("""
                    INSERT INTO commission_record(tenant_id, order_id, employee_id, commission_type, rule_snapshot,
                                                  base_amount, rate, amount, status)
                    VALUES (?,?,?, 'SALES', ?,?,?,?, 'PENDING')
                    """, tid, orderId, req.salesmanId(), "门市成单比例 " + rate + "%", total, rate, amount);
        }
    }

    private String buildCommissionSnapshot(Long tid, CreateOrderReq req) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        if (req.channelId() != null) {
            List<Map<String, Object>> rows = jdbc.queryForList("SELECT commission_rate, commission_ladder FROM channel WHERE tenant_id=? AND id=?",
                    tid, req.channelId());
            if (!rows.isEmpty()) snapshot.put("channel", rows.get(0));
        }
        return json(snapshot);
    }

    private BigDecimal addonPrice(Long tid, Long addonId, Long packageId, Long storeId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT price FROM pkg_addon_price
                WHERE tenant_id=? AND addon_id=? AND (package_id=? OR package_id IS NULL) AND (store_id=? OR store_id IS NULL)
                ORDER BY (package_id IS NOT NULL) DESC, (store_id IS NOT NULL) DESC LIMIT 1
                """, tid, addonId, packageId, storeId);
        if (!rows.isEmpty()) return (BigDecimal) rows.get(0).get("price");
        BigDecimal base = jdbc.queryForObject("SELECT base_price FROM pkg_addon WHERE tenant_id=? AND id=?",
                BigDecimal.class, tid, addonId);
        return base == null ? BigDecimal.ZERO : base;
    }

    private void logStatus(Long tid, Long orderId, String from, String to, String action, String operator, String remark) {
        com.wedding.common.context.LoginContext.LoginUser user = com.wedding.common.context.LoginContext.get();
        jdbc.update("""
                INSERT INTO order_status_log(tenant_id, order_id, from_status, to_status, action, operator_id, operator_name, remark)
                VALUES (?,?,?,?,?,?,?,?)
                """, tid, orderId, from, to, action,
                user == null ? null : user.getId(), operator == null && user != null ? user.getUsername() : operator, remark);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new BizException(500, "数据序列化失败");
        }
    }

    private void publish(String node, Long orderId, Object orderNo, Map<String, Object> payload) {
        try {
            Map<String, Object> msg = new LinkedHashMap<>(payload);
            msg.put("tenantId", TenantContext.requireTenantId());
            msg.put("node", node);
            msg.put("orderId", orderId);
            msg.put("orderNo", orderNo);
            rabbitTemplate.convertAndSend("msg.notify", msg);
        } catch (Exception ignored) {
            // 通知失败不影响主交易
        }
    }

    public record CreateOrderReq(Long storeId, Long memberId, String customerName, String customerPhone,
                                 Long packageId, LocalDate shootDate, LocalTime shootStart, LocalTime shootEnd,
                                 List<Long> resourceIds, List<AddonReq> addons, Long channelId, Long salesmanId,
                                 String source, String remark) {
    }

    public record AddonReq(Long addonId, int quantity) {
    }
}
