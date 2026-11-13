package com.wedding.biz.entry;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.wedding.common.api.BizException;
import com.wedding.common.context.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

/** 多端入口、二维码生成与预览。 */
@Service
@RequiredArgsConstructor
public class EntryService {
    private final JdbcTemplate jdbc;

    @Value("${wedding.entry.base-url:http://localhost:8080}")
    private String baseUrl;

    public List<Map<String, Object>> list() {
        Long tid = TenantContext.requireTenantId();
        return jdbc.queryForList("""
                SELECT q.*, s.store_name, c.channel_name FROM qr_entry q
                LEFT JOIN sys_store s ON s.id=q.store_id AND s.tenant_id=q.tenant_id
                LEFT JOIN channel c ON c.id=q.channel_id AND c.tenant_id=q.tenant_id
                WHERE q.tenant_id=? ORDER BY q.end_type, q.id
                """, tid);
    }

    public Map<String, Object> save(Map<String, Object> body) {
        Long tid = TenantContext.requireTenantId();
        String endType = String.valueOf(body.getOrDefault("endType", "USER_MINI"));
        String url = body.get("url") == null ? defaultUrl(endType) : String.valueOf(body.get("url"));
        if (body.get("id") != null) {
            jdbc.update("""
                    UPDATE qr_entry SET entry_name=?, end_type=?, url=?, store_id=?, channel_id=?, status=?
                    WHERE tenant_id=? AND id=?
                    """, body.get("entryName"), endType, url, body.get("storeId"), body.get("channelId"),
                    body.getOrDefault("status", 1), tid, body.get("id"));
            return Map.of("id", body.get("id"), "url", url);
        }
        org.springframework.jdbc.support.KeyHolder kh = new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbc.update(con -> {
            var ps = con.prepareStatement("""
                    INSERT INTO qr_entry(tenant_id, entry_name, end_type, url, store_id, channel_id, status)
                    VALUES (?,?,?,?,?,?,?)
                    """, java.sql.Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, tid);
            ps.setString(2, String.valueOf(body.getOrDefault("entryName", endType)));
            ps.setString(3, endType);
            ps.setString(4, url);
            ps.setObject(5, body.get("storeId"));
            ps.setObject(6, body.get("channelId"));
            ps.setObject(7, body.getOrDefault("status", 1));
            return ps;
        }, kh);
        return Map.of("id", kh.getKey(), "url", url);
    }

    private String defaultUrl(String endType) {
        Long tid = TenantContext.requireTenantId();
        String code;
        try {
            code = jdbc.queryForObject("SELECT tenant_code FROM platform_tenant WHERE tenant_id=?", String.class, tid);
        } catch (Exception e) {
            code = "demo";
        }
        return switch (endType) {
            case "USER_MINI" -> baseUrl + "/mini/user?tenant=" + code;
            case "STAFF_MINI" -> baseUrl + "/mini/staff?tenant=" + code;
            case "SUPPLIER_MINI" -> baseUrl + "/mini/supplier?tenant=" + code;
            case "CHANNEL_MINI" -> baseUrl + "/mini/channel?tenant=" + code;
            case "MP" -> baseUrl + "/mp?tenant=" + code;
            case "H5" -> baseUrl + "/h5?tenant=" + code;
            case "APP" -> baseUrl + "/app/download?tenant=" + code;
            case "PC" -> baseUrl + "/?tenant=" + code;
            default -> baseUrl;
        };
    }

    public byte[] qrPng(Long entryId, int size) {
        Long tid = TenantContext.requireTenantId();
        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM qr_entry WHERE tenant_id=? AND id=?", tid, entryId);
        String content = String.valueOf(row.get("url"));
        try {
            BitMatrix matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new BizException(500, "二维码生成失败");
        }
    }

    public Map<String, Object> preview(Long entryId) {
        Long tid = TenantContext.requireTenantId();
        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM qr_entry WHERE tenant_id=? AND id=?", tid, entryId);
        return Map.of("entry", row, "qrApi", "/api/tenant/entries/" + entryId + "/qr.png");
    }
}
