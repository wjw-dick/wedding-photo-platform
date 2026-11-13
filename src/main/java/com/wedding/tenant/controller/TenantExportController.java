package com.wedding.platform.controller;

import com.wedding.common.context.TenantContext;
import com.wedding.common.security.RequirePermission;
import com.wedding.tenant.service.GenericCrudService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/** 租户边界内的数据导出（CSV，Excel 可直接打开），仅当前租户。 */
@RestController
@RequestMapping("/api/tenant/export")
@RequiredArgsConstructor
public class TenantExportController {
    private final JdbcTemplate jdbc;

    @GetMapping("/{type}")
    @RequirePermission("report:view")
    public ResponseEntity<byte[]> export(@PathVariable String type) {
        Long tid = TenantContext.requireTenantId();
        String sql = switch (type) {
            case "orders" -> """
                    SELECT order_no AS 订单号, customer_name AS 客户, customer_phone AS 手机号, status AS 状态,
                           total_amount AS 总金额, paid_amount AS 已付, refund_amount AS 退款, shoot_date AS 拍摄日期,
                           created_at AS 创建时间
                    FROM order_main WHERE tenant_id=? AND deleted=0 ORDER BY id
                    """;
            case "members" -> """
                    SELECT id AS 会员ID, nickname AS 昵称, phone AS 手机号, points AS 积分, balance AS 余额,
                           source AS 来源, created_at AS 注册时间
                    FROM member WHERE tenant_id=? AND deleted=0 ORDER BY id
                    """;
            case "leads" -> """
                    SELECT name AS 姓名, phone AS 手机号, source AS 来源, status AS 状态, protect_until AS 保护期,
                           created_at AS 创建时间 FROM crm_lead WHERE tenant_id=? AND deleted=0 ORDER BY id
                    """;
            case "channels" -> """
                    SELECT channel_no AS 渠道编号, channel_name AS 渠道名称, commission_rate AS 佣金比例,
                           credit_limit AS 额度, status AS 状态 FROM channel WHERE tenant_id=? AND deleted=0 ORDER BY id
                    """;
            case "audits" -> """
                    SELECT module AS 模块, action AS 动作, object_type AS 对象, object_id AS 对象ID,
                           detail AS 详情, result AS 结果, ip AS IP, created_at AS 时间
                    FROM sys_audit WHERE tenant_id=? ORDER BY id
                    """;
            default -> throw new IllegalArgumentException("不支持的导出类型：" + type);
        };
        List<Map<String, Object>> rows = jdbc.queryForList(sql, tid);
        StringBuilder sb = new StringBuilder();
        if (!rows.isEmpty()) {
            sb.append(String.join(",", rows.get(0).keySet())).append('\n');
            for (Map<String, Object> row : rows) {
                boolean first = true;
                for (Object v : row.values()) {
                    if (!first) sb.append(',');
                    first = false;
                    String s = v == null ? "" : String.valueOf(v);
                    if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
                        s = "\"" + s.replace("\"", "\"\"") + "\"";
                    }
                    sb.append(s);
                }
                sb.append('\n');
            }
        }
        byte[] bytes = ("\uFEFF" + sb).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + type + ".csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(bytes);
    }
}
