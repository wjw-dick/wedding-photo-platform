package com.wedding.common.security;

import com.wedding.common.api.BizException;
import com.wedding.common.context.LoginContext;
import com.wedding.common.context.TenantContext;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {
    private final JwtService jwtService;
    private final PermissionService permissionService;
    private final TenantGuard tenantGuard;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        String token = extractToken(request);
        if (token == null) {
            throw new BizException(401, "未登录或登录已过期");
        }
        Claims claims;
        try {
            claims = jwtService.parse(token);
        } catch (Exception e) {
            throw new BizException(401, "登录已过期，请重新登录");
        }
        Long userId = Long.valueOf(claims.getSubject());
        Long tenantId = ((Number) claims.get("tenantId")).longValue();
        String userType = (String) claims.get("userType");
        String platformRole = (String) claims.get("platformRole");
        boolean platformPath = path.startsWith("/api/platform");

        LoginContext.LoginUser user;
        if (userType != null && userType.startsWith("PLATFORM_")) {
            if (!platformPath) {
                throw BizException.forbidden("平台账号不可访问租户端");
            }
            user = LoginContext.LoginUser.builder()
                    .id(userId).username((String) claims.get("username"))
                    .userType(userType).platformRole(platformRole)
                    .permissions(platformPermissions(platformRole)).ip(clientIp(request)).build();
        } else {
            if (platformPath) {
                throw BizException.forbidden("租户账号不可访问平台端");
            }
            if (tenantId == null || tenantId <= 0) {
                throw new BizException(401, "登录态缺少租户信息");
            }
            TenantContext.set(tenantId, (String) claims.get("tenantCode"));
            boolean member = "MEMBER".equals(userType);
            Set<String> perms = member ? Set.of() : permissionService.permissions(tenantId, userId);
            user = LoginContext.LoginUser.builder()
                    .id(userId).tenantId(tenantId)
                    .tenantCode((String) claims.get("tenantCode"))
                    .username((String) claims.get("username"))
                    .realName((String) claims.get("username"))
                    .userType(userType)
                    .storeId(((Number) claims.get("storeId")).longValue())
                    .dataScope(member ? "SELF" : permissionService.dataScope(tenantId, userId))
                    .permissions(perms).ip(clientIp(request)).build();
            tenantGuard.check(tenantId, !"GET".equalsIgnoreCase(request.getMethod()));
        }
        LoginContext.set(user);

        if (handler instanceof HandlerMethod hm) {
            RequirePermission rp = hm.getMethodAnnotation(RequirePermission.class);
            if (rp == null) {
                rp = hm.getBeanType().getAnnotation(RequirePermission.class);
            }
            if (rp != null && !user.hasPermission(rp.value())) {
                throw BizException.forbidden("无权限执行该操作：" + rp.value());
            }
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        LoginContext.clear();
        TenantContext.clear();
    }

    private Set<String> platformPermissions(String role) {
        if (role == null) return Set.of();
        return switch (role) {
            case "PLATFORM_ADMIN" -> Set.of("*");
            case "PLATFORM_OPERATOR" -> Set.of("tenant:*", "plan:*", "announcement:*", "dashboard:read", "audit:read", "bill:read");
            case "PLATFORM_FINANCE" -> Set.of("bill:*", "dashboard:read", "tenant:read", "audit:read");
            default -> Set.of();
        };
    }

    private String extractToken(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            return auth.substring(7);
        }
        return null;
    }

    private String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        return xff == null || xff.isBlank() ? request.getRemoteAddr() : xff.split(",")[0].trim();
    }
}
