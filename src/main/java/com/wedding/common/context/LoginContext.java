package com.wedding.common.context;

import com.wedding.common.api.BizException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

/** 登录态（租户账号 / 平台账号 / 会员）。 */
public final class LoginContext {
    private static final ThreadLocal<LoginUser> USER = new ThreadLocal<>();

    private LoginContext() {
    }

    public static void set(LoginUser user) {
        USER.set(user);
    }

    public static LoginUser get() {
        return USER.get();
    }

    public static LoginUser require() {
        LoginUser u = USER.get();
        if (u == null) {
            throw new BizException(401, "未登录或登录已过期");
        }
        return u;
    }

    public static void clear() {
        USER.remove();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoginUser {
        private Long id;
        private Long tenantId;
        private String tenantCode;
        private String username;
        private String realName;
        private String userType;
        private Long storeId;
        private String platformRole;
        private String dataScope;
        private Set<String> permissions;
        private String ip;

        public boolean isPlatform() {
            return userType != null && userType.startsWith("PLATFORM_");
        }

        public boolean hasPermission(String permission) {
            if (permissions == null) return false;
            if (permissions.contains("*") || permissions.contains(permission)) return true;
            for (String p : permissions) {
                if (p.endsWith("*") && permission.startsWith(p.substring(0, p.length() - 1))) return true;
            }
            return false;
        }
    }
}
