package com.wedding.common.audit;

import com.wedding.common.context.LoginContext;
import com.wedding.common.context.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {
    private final JdbcTemplate jdbc;

    public void record(String module, String action, String targetType, String targetId,
                       String detail, String result, long costMs) {
        LoginContext.LoginUser user = LoginContext.get();
        Long tenantId = TenantContext.getTenantId();
        try {
            if (user != null && user.isPlatform()) {
                jdbc.update("""
                        INSERT INTO platform_audit(operator_id, operator_name, action, target_tenant_id, detail, result, ip, created_at)
                        VALUES (?,?,?,?,?,?,?,?)
                        """, user.getId(), user.getUsername(), module + ":" + action,
                        tenantId, detail, result, user.getIp(), LocalDateTime.now());
            } else {
                jdbc.update("""
                        INSERT INTO sys_audit(tenant_id, store_id, operator_id, operator_name, module, action,
                                              target_type, target_id, detail, result, ip, cost_ms, created_at)
                        VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
                        """, tenantId == null ? 0L : tenantId,
                        user == null ? null : user.getStoreId(),
                        user == null ? null : user.getId(),
                        user == null ? "system" : user.getUsername(),
                        module, action, targetType, targetId,
                        detail == null ? null : detail.substring(0, Math.min(detail.length(), 1900)),
                        result, user == null ? null : user.getIp(), costMs, LocalDateTime.now());
            }
        } catch (Exception e) {
            log.warn("审计日志写入失败: {}", e.getMessage());
        }
    }
}
