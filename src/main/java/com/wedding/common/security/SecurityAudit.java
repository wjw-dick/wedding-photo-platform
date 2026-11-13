package com.wedding.common.security;

import com.wedding.common.context.LoginContext;
import com.wedding.common.context.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 安全审计：跨租户越权尝试独立事务落库，避免被业务异常回滚。 */
@Service
@RequiredArgsConstructor
public class SecurityAudit {
    private final JdbcTemplate jdbc;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void denied(String table, Long targetId, String label) {
        Long tid = TenantContext.getTenantId();
        if (tid == null) return;
        LoginContext.LoginUser user = LoginContext.get();
        try {
            jdbc.update("""
                    INSERT INTO sys_audit(tenant_id, store_id, operator_id, operator_name, module, action,
                                          target_type, target_id, detail, result, ip)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?)
                    """, tid, user == null ? null : user.getStoreId(),
                    user == null ? null : user.getId(),
                    user == null ? "unknown" : (user.getRealName() == null ? user.getUsername() : user.getRealName()),
                    "security", "CROSS_TENANT_DENIED", table, String.valueOf(targetId),
                    "尝试访问其他租户的" + label, "DENIED", user == null ? null : user.getIp());
        } catch (Exception ignored) {
            // 审计失败不改变拒绝结果，避免越权请求因审计异常变为 500
        }
    }
}
