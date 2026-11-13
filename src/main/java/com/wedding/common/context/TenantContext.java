package com.wedding.common.context;

import com.wedding.common.api.BizException;

/** 请求级租户上下文；跨租户访问必须显式切换并复原。 */
public final class TenantContext {
    private static final ThreadLocal<Long> TENANT = new ThreadLocal<>();
    private static final ThreadLocal<String> CODE = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(Long tenantId, String tenantCode) {
        TENANT.set(tenantId);
        CODE.set(tenantCode);
    }

    public static Long getTenantId() {
        return TENANT.get();
    }

    public static String getTenantCode() {
        return CODE.get();
    }

    public static Long requireTenantId() {
        Long id = TENANT.get();
        if (id == null) {
            throw new BizException(401, "缺少租户上下文");
        }
        return id;
    }

    public static void clear() {
        TENANT.remove();
        CODE.remove();
    }
}
