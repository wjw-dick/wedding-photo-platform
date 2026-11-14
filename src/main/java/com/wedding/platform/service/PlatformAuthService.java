package com.wedding.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wedding.common.api.BizException;
import com.wedding.common.context.LoginContext;
import com.wedding.common.security.JwtService;
import com.wedding.platform.entity.PlatformAdmin;
import com.wedding.platform.mapper.PlatformAdminMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PlatformAuthService {
    private final PlatformAdminMapper adminMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JdbcTemplate jdbc;

    public Map<String, Object> login(String username, String password) {
        PlatformAdmin admin = adminMapper.selectOne(new LambdaQueryWrapper<PlatformAdmin>()
                .eq(PlatformAdmin::getUsername, username));
        if (admin == null || admin.getStatus() != 1 || !passwordEncoder.matches(password, admin.getPassword())) {
            throw new BizException(401, "账号或密码错误");
        }
        adminMapper.updateById(new PlatformAdmin() {{
            setId(admin.getId());
            setLastLoginAt(LocalDateTime.now());
        }});
        String token = jwtService.issue(admin.getId(), null, null, admin.getUsername(),
                admin.getRole(), null, admin.getRole());
        jdbc.update("INSERT INTO platform_audit(operator_id, operator_name, action, result, created_at) VALUES (?,?,?,?,NOW())",
                admin.getId(), admin.getUsername(), "LOGIN", "SUCCESS");
        return Map.of(
                "token", token,
                "user", Map.of("id", admin.getId(), "username", admin.getUsername(),
                        "realName", admin.getRealName() == null ? "" : admin.getRealName(),
                        "role", admin.getRole()));
    }

    public Map<String, Object> me() {
        LoginContext.LoginUser user = LoginContext.require();
        return Map.of("id", user.getId(), "username", user.getUsername(), "role", user.getPlatformRole(),
                "permissions", user.getPermissions());
    }

    public void changePassword(String oldPassword, String newPassword) {
        LoginContext.LoginUser user = LoginContext.require();
        PlatformAdmin admin = adminMapper.selectById(user.getId());
        if (admin == null) throw new BizException(404, "账号不存在");
        if (!passwordEncoder.matches(oldPassword, admin.getPassword())) {
            throw new BizException(400, "原密码不正确");
        }
        if (newPassword == null || newPassword.length() < 6) {
            throw new BizException(400, "新密码至少 6 位");
        }
        admin.setPassword(passwordEncoder.encode(newPassword));
        adminMapper.updateById(admin);
        jdbc.update("INSERT INTO platform_audit(operator_id, operator_name, action, result, created_at) VALUES (?,?,?,?,NOW())",
                user.getId(), user.getUsername(), "CHANGE_PASSWORD", "SUCCESS");
    }
}
