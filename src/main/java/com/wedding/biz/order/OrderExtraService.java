package com.wedding.biz.order;

import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfWriter;
import com.wedding.common.api.BizException;
import com.wedding.common.api.PageResult;
import com.wedding.common.context.LoginContext;
import com.wedding.common.context.TenantContext;
import com.wedding.common.storage.StorageManager;
import com.wedding.common.util.BizNo;
import com.wedding.biz.schedule.ScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

/** 改期/取消、合同模板与 PDF、发票。 */
@Service
@RequiredArgsConstructor
public class OrderExtraService {
    private static final String DEFAULT_CONTRACT = """
            甲方（客户）：{{customerName}}
            乙方：{{tenantName}}
            拍摄日期：{{shootDate}}
            套系：{{packageName}}
            合同金额：{{totalAmount}} 元
            定金：{{depositAmount}} 元

            一、乙方按约定提供拍摄、精修与交付服务。
            二、甲方应按约定支付定金与尾款。
            三、改期与退订按门店公示规则执行。
            四、客片肖像使用须另行授权。
            五、客片保留期 90 天，逾期可申请延期一次。
            """;

    private final JdbcTemplate jdbc;
    private final BizNo bizNo;
    private final ScheduleService scheduleService;
    private final StorageManager storageManager;

    @Transactional
    public Map<String, Object> reschedule(Long orderId, String newDate, String newStart, String newEnd,
                                          List<Long> resourceIds, String reason, String diffRule) {
        Long tid = TenantContext.requireTenantId();
        Map<String, Object> order = jdbc.queryForMap(
                "SELECT * FROM order_main WHERE tenant_id=? AND id=? AND deleted=0 FOR UPDATE", tid, orderId);
        if (List.of("COMPLETED", "CANCELLED").contains(String.valueOf(order.get("status")))) {
            throw new BizException("已完成或已取消订单不可改期");
        }
        String orderNo = String.valueOf(order.get("order_no"));
        List<Map<String, Object>> oldSlots = jdbc.queryForList("""
                SELECT sl.*, l.lock_key FROM sch_slot sl
                LEFT JOIN sch_lock l ON l.tenant_id=sl.tenant_id AND l.order_no=?
                     AND l.resource_id=sl.resource_id AND l.slot_date=sl.slot_date AND l.slot_start=sl.slot_start
                WHERE sl.tenant_id=? AND sl.order_id=?
                """, orderNo, tid, orderId);
        List<Map<String, Object>> newLocks = new ArrayList<>();
        if (newDate != null && !newDate.isBlank() && resourceIds != null) {
            LocalTime start = LocalTime.parse(newStart == null ? "09:00" : newStart);
            LocalTime end = LocalTime.parse(newEnd == null ? "18:00" : newEnd);
            for (Long resourceId : resourceIds) {
                newLocks.add(scheduleService.lock(resourceId, LocalDate.parse(newDate), start, end, orderNo,
                        order.get("member_id") == null ? null : ((Number) order.get("member_id")).longValue()));
            }
        }
        for (Map<String, Object> slot : oldSlots) {
            jdbc.update("""
                    UPDATE sch_slot SET status=IF(used_count-1<=0,'OPEN','LOCKED'), used_count=GREATEST(used_count-1,0),
                                        order_id=NULL
                    WHERE tenant_id=? AND id=?
                    """, tid, slot.get("id"));
            jdbc.update("UPDATE sch_lock SET status='RELEASED' WHERE tenant_id=? AND order_no=? AND status IN ('LOCKED','CONFIRMED')",
                    tid, orderNo);
        }
        jdbc.update("""
                UPDATE order_main SET shoot_date=COALESCE(?,shoot_date), abnormal_status='RESCHEDULED' WHERE tenant_id=? AND id=?
                """, newDate, tid, orderId);
        jdbc.update("""
                INSERT INTO order_status_log(tenant_id, order_id, from_status, to_status, action, operator_id, operator_name, remark)
                VALUES (?,?,?,?, 'RESCHEDULE', ?,?,?)
                """, tid, orderId, order.get("status"), order.get("status"), LoginContext.require().getId(),
                LoginContext.require().getUsername(), "改期至 " + newDate + "：" + reason);
        for (Map<String, Object> lock : newLocks) {
            scheduleService.confirm(String.valueOf(lock.get("lockKey")), orderId);
        }
        return Map.of("orderId", orderId, "newDate", newDate, "diffRule", diffRule == null ? "" : diffRule,
                "locks", newLocks);
    }

    @Transactional
    public void cancel(Long orderId, String reason, boolean refundDeposit) {
        Long tid = TenantContext.requireTenantId();
        Map<String, Object> order = jdbc.queryForMap(
                "SELECT * FROM order_main WHERE tenant_id=? AND id=? AND deleted=0 FOR UPDATE", tid, orderId);
        if ("COMPLETED".equals(order.get("status"))) throw new BizException("已完成订单不可取消");
        jdbc.update("""
                UPDATE order_main SET status='CANCELLED', abnormal_status='CANCELLED' WHERE tenant_id=? AND id=?
                """, tid, orderId);
        jdbc.update("UPDATE sch_lock SET status='RELEASED' WHERE tenant_id=? AND order_no=? AND status IN ('LOCKED','CONFIRMED')",
                tid, order.get("order_no"));
        jdbc.update("""
                UPDATE sch_slot SET status=IF(used_count-1<=0,'OPEN','LOCKED'), used_count=GREATEST(used_count-1,0), order_id=NULL
                WHERE tenant_id=? AND order_id=?
                """, tid, orderId);
        jdbc.update("""
                INSERT INTO order_status_log(tenant_id, order_id, from_status, to_status, action, operator_id, operator_name, remark)
                VALUES (?,?,?, 'CANCELLED', 'CANCEL', ?,?,?)
                """, tid, orderId, order.get("status"), LoginContext.require().getId(),
                LoginContext.require().getUsername(), reason);
        if (refundDeposit && ((BigDecimal) order.get("paid_amount")).compareTo(BigDecimal.ZERO) > 0) {
            jdbc.update("""
                    INSERT INTO refund_record(tenant_id, order_id, refund_no, amount, reason, status)
                    VALUES (?,?,?,?,?, 'PENDING')
                    """, tid, orderId, bizNo.next("RF"), order.get("paid_amount"), "取消订单退款：" + reason);
        }
    }

    // ---------------- 合同 ----------------

    public List<Map<String, Object>> templates() {
        Long tid = TenantContext.requireTenantId();
        return jdbc.queryForList("SELECT * FROM contract_template WHERE tenant_id=? AND deleted=0 ORDER BY id", tid);
    }

    public Long saveTemplate(Map<String, Object> body) {
        Long tid = TenantContext.requireTenantId();
        if (body.get("id") != null) {
            jdbc.update("UPDATE contract_template SET template_name=?, content=?, status=? WHERE tenant_id=? AND id=?",
                    body.get("templateName"), body.get("content"), body.getOrDefault("status", 1), tid, body.get("id"));
            return Long.valueOf(String.valueOf(body.get("id")));
        }
        org.springframework.jdbc.support.KeyHolder kh = new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbc.update(con -> {
            var ps = con.prepareStatement("""
                    INSERT INTO contract_template(tenant_id, template_name, content, status) VALUES (?,?,?,?)
                    """, java.sql.Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, tid);
            ps.setString(2, String.valueOf(body.get("templateName")));
            ps.setString(3, String.valueOf(body.get("content")));
            ps.setObject(4, body.getOrDefault("status", 1));
            return ps;
        }, kh);
        return Objects.requireNonNull(kh.getKey()).longValue();
    }

    /** 按模板渲染合同并生成 PDF，存入对象存储；返回合同记录。 */
    @Transactional
    public Map<String, Object> prepareContract(Long orderId, Long templateId) {
        Long tid = TenantContext.requireTenantId();
        Map<String, Object> order = jdbc.queryForMap("""
                SELECT o.*, p.name AS package_name FROM order_main o
                LEFT JOIN pkg_package p ON p.id=o.package_id AND p.tenant_id=o.tenant_id
                WHERE o.tenant_id=? AND o.id=?
                """, tid, orderId);
        Map<String, Object> template;
        if (templateId == null) {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT * FROM contract_template WHERE tenant_id=? AND deleted=0 ORDER BY id LIMIT 1", tid);
            template = rows.isEmpty() ? Map.of("template_name", "婚纱摄影服务合同（默认）", "content", DEFAULT_CONTRACT)
                    : rows.get(0);
        } else {
            template = jdbc.queryForMap(
                    "SELECT * FROM contract_template WHERE tenant_id=? AND id=?", tid, templateId);
        }
        String tenantName = jdbc.queryForObject("SELECT tenant_name FROM platform_tenant WHERE tenant_id=?", String.class, tid);
        String content = String.valueOf(template.get("content"))
                .replace("{{customerName}}", String.valueOf(order.get("customer_name")))
                .replace("{{tenantName}}", tenantName)
                .replace("{{shootDate}}", String.valueOf(order.get("shoot_date")))
                .replace("{{packageName}}", String.valueOf(order.get("package_name")))
                .replace("{{totalAmount}}", String.valueOf(order.get("total_amount")))
                .replace("{{depositAmount}}", String.valueOf(order.get("deposit_amount")));
        byte[] pdf = pdf(content);
        StorageManager.UploadResult up = storageManager.upload(tid, "CONTRACT", String.valueOf(order.get("order_no")),
                "contract-" + order.get("order_no") + ".pdf", new ByteArrayInputStream(pdf), pdf.length, "application/pdf");
        org.springframework.jdbc.support.KeyHolder kh = new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbc.update(con -> {
            var ps = con.prepareStatement("""
                    INSERT INTO order_contract(tenant_id, contract_name, content, status) VALUES (?,?,?,1)
                    """, java.sql.Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, tid);
            ps.setString(2, template.get("template_name") + " - " + order.get("order_no"));
            ps.setString(3, content);
            return ps;
        }, kh);
        Long contractId = Objects.requireNonNull(kh.getKey()).longValue();
        return Map.of("contractId", contractId, "content", content,
                "pdfUrl", storageManager.signedUrl(tid, up.provider(), up.objectKey(), 30),
                "objectKey", up.objectKey());
    }

    private byte[] pdf(String text) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document();
            PdfWriter.getInstance(doc, out);
            doc.open();
            BaseFont bf;
            try {
                bf = BaseFont.createFont("STSong-Light", "UniGB-UCS2-H", BaseFont.NOT_EMBEDDED);
            } catch (Exception e) {
                bf = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
            }
            Font font = new Font(bf, 12);
            for (String line : text.split("\n")) {
                doc.add(new Paragraph(line, font));
            }
            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new BizException(500, "合同 PDF 生成失败：" + e.getMessage());
        }
    }

    // ---------------- 发票 ----------------

    public PageResult<Map<String, Object>> invoices(String status, long page, long size) {
        Long tid = TenantContext.requireTenantId();
        String where = " WHERE i.tenant_id=?" + (status == null || status.isBlank() ? "" : " AND i.status=?");
        List<Object> args = new ArrayList<>(List.of(tid));
        if (status != null && !status.isBlank()) args.add(status);
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM order_invoice i" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add((page - 1) * size);
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT i.*, o.order_no, o.customer_name FROM order_invoice i
                LEFT JOIN order_main o ON o.id=i.order_id AND o.tenant_id=i.tenant_id
                """ + where + " ORDER BY i.id DESC LIMIT ? OFFSET ?", pageArgs.toArray());
        return PageResult.of(total == null ? 0 : total, page, size, rows);
    }

    public Long applyInvoice(Long orderId, String titleType, String title, String taxNo, BigDecimal amount) {
        Long tid = TenantContext.requireTenantId();
        org.springframework.jdbc.support.KeyHolder kh = new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbc.update(con -> {
            var ps = con.prepareStatement("""
                    INSERT INTO order_invoice(tenant_id, order_id, title_type, title, tax_no, amount, status)
                    VALUES (?,?,?,?,?,?, 'APPLIED')
                    """, java.sql.Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, tid);
            ps.setLong(2, orderId);
            ps.setString(3, titleType == null ? "PERSONAL" : titleType);
            ps.setString(4, title);
            ps.setString(5, taxNo);
            ps.setBigDecimal(6, amount);
            return ps;
        }, kh);
        return Objects.requireNonNull(kh.getKey()).longValue();
    }

    public void issueInvoice(Long invoiceId, String invoiceNo, String remark) {
        Long tid = TenantContext.requireTenantId();
        jdbc.update("""
                UPDATE order_invoice SET status='ISSUED', invoice_no=?, issued_at=NOW(), remark=?
                WHERE tenant_id=? AND id=? AND status='APPLIED'
                """, invoiceNo, remark, tid, invoiceId);
    }
}
