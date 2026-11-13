package com.wedding.common.security;

import com.wedding.common.api.BizException;
import com.wedding.common.context.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 租户数据归属校验：记录存在但属于其他租户时返回 403 并写安全审计，
 * 彻底不存在时返回 404。用于按 ID 直接访问明细的场景。
 */
@Service
@RequiredArgsConstructor
public class TenantDataGuard {
    private final JdbcTemplate jdbc;
    private final SecurityAudit securityAudit;

    /**
     * @param table 表名，仅允许代码内硬编码的常量，禁止拼接外部输入
     * @param id    记录主键
     * @param label 业务名称，用于错误提示
     */
    public void requireOwned(String table, Long id, String label) {
        Long tid = TenantContext.requireTenantId();
        Long own = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE tenant_id=? AND id=?", Long.class, tid, id);
        if (own != null && own > 0) {
            return;
        }
        Long any = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE id=?", Long.class, id);
        if (any != null && any > 0) {
            securityAudit.denied(table, id, label);
            throw BizException.forbidden("无权访问其他租户的" + label);
        }
        throw BizException.notFound(label + "不存在");
    }
}
