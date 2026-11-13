package com.wedding.common.security;

import com.wedding.platform.entity.PlatformTenant;
import com.wedding.platform.mapper.PlatformTenantMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 租户生命周期守卫：停用/到期拒绝，欠费只读。带 60 秒本地缓存。 */
@Service
@RequiredArgsConstructor
public class TenantGuard {
    private final PlatformTenantMapper tenantMapper;
    private final Map<Long, CacheEntry> cache = new ConcurrentHashMap<>();

    public PlatformTenant get(Long tenantId) {
        CacheEntry e = cache.get(tenantId);
        long now = System.currentTimeMillis();
        if (e != null && e.expireAt > now) {
            return e.tenant;
        }
        PlatformTenant t = tenantMapper.selectById(tenantId);
        cache.put(tenantId, new CacheEntry(t, now + 60_000));
        return t;
    }

    public void check(Long tenantId, boolean writeOperation) {
        PlatformTenant t = get(tenantId);
        if (t == null) {
            throw new com.wedding.common.api.BizException(403, "租户不存在");
        }
        String status = t.getStatus();
        if ("SUSPENDED".equals(status) || "EXPIRED".equals(status)) {
            throw new com.wedding.common.api.BizException(403, "租户已停用或到期，请联系平台管理员");
        }
        boolean trialOver = "TRIAL".equals(status) && t.getTrialEndAt() != null
                && t.getTrialEndAt().isBefore(LocalDateTime.now());
        if (trialOver) {
            throw new com.wedding.common.api.BizException(403, "试用期已结束，请升级套餐");
        }
        if (writeOperation && ("OVERDUE".equals(status)
                || (t.getExpireAt() != null && t.getExpireAt().isBefore(LocalDateTime.now())))) {
            throw new com.wedding.common.api.BizException(403, "租户已欠费，当前为只读状态，请尽快续费");
        }
    }

    public void evict(Long tenantId) {
        cache.remove(tenantId);
    }

    private record CacheEntry(PlatformTenant tenant, long expireAt) {
    }
}
