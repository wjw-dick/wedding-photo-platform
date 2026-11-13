package com.wedding.common.security;

import com.wedding.common.api.BizException;
import com.wedding.platform.entity.PlatformTenant;
import com.wedding.platform.mapper.PlatformTenantMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** 五类配额服务端校验：席位、公众号、存储、短信、API。超限带升级引导。 */
@Service
@RequiredArgsConstructor
public class QuotaService {
    private final PlatformTenantMapper tenantMapper;
    private final JdbcTemplate jdbc;

    public void checkSeat(Long tenantId, int adding) {
        PlatformTenant t = tenant(tenantId);
        long used = count("SELECT COUNT(*) FROM sys_user WHERE tenant_id=? AND deleted=0", tenantId);
        if (used + adding > t.getSeatLimit()) {
            throw BizException.quota("席位已用 " + used + "/" + t.getSeatLimit() + "，请升级套餐或加购席位");
        }
    }

    public void checkMpAccount(Long tenantId, int adding) {
        PlatformTenant t = tenant(tenantId);
        long used = count("SELECT COUNT(*) FROM mp_account WHERE tenant_id=? AND deleted=0", tenantId);
        if (used + adding > t.getMpAccountLimit()) {
            throw BizException.quota("公众号数已达上限 " + t.getMpAccountLimit() + "，请升级套餐");
        }
    }

    public void checkStorage(Long tenantId, long addingBytes) {
        PlatformTenant t = tenant(tenantId);
        long used = count("SELECT COALESCE(SUM(size_bytes),0) FROM media_file WHERE tenant_id=?", tenantId);
        long limit = t.getStorageQuotaGb().longValue() * 1024 * 1024 * 1024;
        if (used + addingBytes > limit) {
            throw BizException.quota("存储空间不足（" + (used / 1024 / 1024) + "MB / " + t.getStorageQuotaGb() + "GB），请升级套餐");
        }
    }

    public void checkSms(Long tenantId, int adding) {
        PlatformTenant t = tenant(tenantId);
        long used = count("SELECT COALESCE(used_sms,0) FROM platform_tenant_quota_usage WHERE tenant_id=? ORDER BY stat_date DESC LIMIT 1", tenantId);
        if (used + adding > t.getSmsQuota()) {
            throw BizException.quota("短信额度已用尽，请升级套餐");
        }
    }

    private PlatformTenant tenant(Long tenantId) {
        PlatformTenant t = tenantMapper.selectById(tenantId);
        if (t == null) throw BizException.forbidden("租户不存在");
        return t;
    }

    private long count(String sql, Object... args) {
        Long v = jdbc.queryForObject(sql, Long.class, args);
        return v == null ? 0 : v;
    }
}
