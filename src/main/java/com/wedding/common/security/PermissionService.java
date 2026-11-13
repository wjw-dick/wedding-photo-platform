package com.wedding.common.security;

import com.wedding.common.context.LoginContext;
import com.wedding.common.context.TenantContext;
import com.wedding.tenant.entity.SysUser;
import com.wedding.tenant.mapper.SysMenuMapper;
import com.wedding.tenant.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** 从 RBAC 关系加载权限，Redis 缓存 10 分钟；写操作完成后主动失效。 */
@Service
@RequiredArgsConstructor
public class PermissionService {
    private static final String SUPER = "TENANT_ADMIN";
    private final SysMenuMapper menuMapper;
    private final SysUserMapper userMapper;
    private final StringRedisTemplate redis;

    public Set<String> permissions(Long tenantId, Long userId) {
        String key = "tenant:" + tenantId + ":perm:" + userId;
        String cached = redis.opsForValue().get(key);
        if (cached != null) {
            return cached.isEmpty() ? Set.of() : Arrays.stream(cached.split(",")).collect(Collectors.toSet());
        }
        SysUser user = userMapper.selectById(userId);
        Set<String> perms;
        if (user != null && SUPER.equals(user.getUserType())) {
            perms = Set.of("*");
        } else {
            List<String> list = menuMapper.selectPermissions(tenantId, userId);
            perms = new HashSet<>(list);
        }
        redis.opsForValue().set(key, String.join(",", perms), Duration.ofMinutes(10));
        return perms;
    }

    public String dataScope(Long tenantId, Long userId) {
        List<String> scopes = menuMapper.selectDataScopes(tenantId, userId);
        if (scopes.contains("ALL")) return "ALL";
        if (scopes.contains("STORE")) return "STORE";
        return "SELF";
    }

    public void evict(Long tenantId, Long userId) {
        redis.delete("tenant:" + tenantId + ":perm:" + userId);
    }

    public void evictTenant(Long tenantId) {
        Set<String> keys = redis.keys("tenant:" + tenantId + ":perm:*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    public LoginContext.LoginUser loadLoginUser(Long tenantId, Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            return null;
        }
        TenantContext.set(tenantId, null);
        return LoginContext.LoginUser.builder()
                .id(user.getId()).tenantId(tenantId).username(user.getUsername())
                .realName(user.getRealName()).userType(user.getUserType())
                .storeId(user.getStoreId()).permissions(permissions(tenantId, userId)).build();
    }
}
