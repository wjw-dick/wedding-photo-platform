package com.wedding.tenant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wedding.common.api.BizException;
import com.wedding.common.context.LoginContext;
import com.wedding.common.context.TenantContext;
import com.wedding.common.security.JwtService;
import com.wedding.common.security.PermissionService;
import com.wedding.common.security.TenantGuard;
import com.wedding.platform.entity.PlatformTenant;
import com.wedding.platform.mapper.PlatformTenantMapper;
import com.wedding.tenant.entity.SysUser;
import com.wedding.tenant.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class TenantAuthService {
    private final SysUserMapper userMapper;
    private final PlatformTenantMapper tenantMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final PermissionService permissionService;
    private final TenantGuard tenantGuard;
    private final JdbcTemplate jdbc;

    public Map<String, Object> login(String tenantCode, String username, String password) {
        PlatformTenant tenant = tenantMapper.selectOne(new LambdaQueryWrapper<PlatformTenant>()
                .eq(PlatformTenant::getTenantCode, tenantCode));
        if (tenant == null) {
            throw new BizException(401, "租户不存在");
        }
        TenantContext.set(tenant.getTenantId(), tenantCode);
        try {
            tenantGuard.check(tenant.getTenantId(), false);
            SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getUsername, username));
            if (user == null || user.getStatus() != 1 || !passwordEncoder.matches(password, user.getPassword())) {
                throw new BizException(401, "账号或密码错误");
            }
            userMapper.updateById(new SysUser() {{
                setId(user.getId());
                setLastLoginAt(LocalDateTime.now());
            }});
            Set<String> perms = permissionService.permissions(tenant.getTenantId(), user.getId());
            String token = jwtService.issue(user.getId(), tenant.getTenantId(), tenantCode,
                    user.getUsername(), user.getUserType(), user.getStoreId(), null);
            jdbc.update("INSERT INTO sys_login_log(tenant_id,user_id,username,user_type,success,created_at) VALUES (?,?,?,?,1,NOW())",
                    tenant.getTenantId(), user.getId(), username, user.getUserType());
            return Map.of(
                    "token", token,
                    "user", Map.of("id", user.getId(), "username", user.getUsername(),
                            "realName", user.getRealName() == null ? "" : user.getRealName(),
                            "userType", user.getUserType(),
                            "storeId", user.getStoreId() == null ? 0 : user.getStoreId(),
                            "permissions", perms),
                    "tenant", Map.of("id", tenant.getTenantId(), "code", tenantCode,
                            "name", tenant.getTenantName(), "status", tenant.getStatus()));
        } finally {
            TenantContext.clear();
        }
    }

    public Map<String, Object> me() {
        LoginContext.LoginUser user = LoginContext.require();
        return Map.of("id", user.getId(), "username", user.getUsername(), "realName", user.getRealName(),
                "userType", user.getUserType(), "storeId", user.getStoreId(),
                "permissions", user.getPermissions());
    }

    public void changePassword(String oldPassword, String newPassword) {
        LoginContext.LoginUser user = LoginContext.require();
        SysUser u = userMapper.selectById(user.getId());
        if (u == null) throw new BizException(404, "账号不存在");
        if (!passwordEncoder.matches(oldPassword, u.getPassword())) {
            throw new BizException(400, "原密码不正确");
        }
        if (newPassword == null || newPassword.length() < 6) {
            throw new BizException(400, "新密码至少 6 位");
        }
        u.setPassword(passwordEncoder.encode(newPassword));
        userMapper.updateById(u);
    }
}
