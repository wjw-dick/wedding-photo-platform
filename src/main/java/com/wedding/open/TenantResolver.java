package com.wedding.open;

import com.wedding.common.api.BizException;
import com.wedding.common.context.TenantContext;
import com.wedding.platform.entity.PlatformTenant;
import com.wedding.platform.mapper.PlatformTenantMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TenantResolver {
    private final PlatformTenantMapper tenantMapper;

    public PlatformTenant byCode(String code) {
        PlatformTenant tenant = tenantMapper.selectOne(new LambdaQueryWrapper<PlatformTenant>()
                .eq(PlatformTenant::getTenantCode, code));
        if (tenant == null) throw BizException.notFound("租户不存在");
        if ("SUSPENDED".equals(tenant.getStatus()) || "EXPIRED".equals(tenant.getStatus())) {
            throw BizException.forbidden("租户已停用");
        }
        TenantContext.set(tenant.getTenantId(), tenant.getTenantCode());
        return tenant;
    }
}
