package com.wedding.init;

import com.wedding.platform.service.PlatformTenantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final PlatformTenantService tenantService;

    @Override
    public void run(ApplicationArguments args) {
        Long admins = jdbc.queryForObject("SELECT COUNT(*) FROM platform_admin", Long.class);
        if (admins == null || admins == 0) {
            jdbc.update("""
                    INSERT INTO platform_admin(username, password, real_name, role, status)
                    VALUES (?,?,?,?,1)
                    """, "admin", passwordEncoder.encode("admin123"), "平台超管", "PLATFORM_ADMIN");
            jdbc.update("""
                    INSERT INTO platform_admin(username, password, real_name, role, status)
                    VALUES (?,?,?,?,1)
                    """, "finance", passwordEncoder.encode("finance123"), "平台财务", "PLATFORM_FINANCE");
            log.info("已初始化平台账号：admin/admin123（超管）、finance/finance123（财务）");
        }
        Long tenants = jdbc.queryForObject("SELECT COUNT(*) FROM platform_tenant", Long.class);
        if (tenants == null || tenants == 0) {
            Long planId = jdbc.queryForObject("SELECT plan_id FROM platform_plan ORDER BY price LIMIT 1", Long.class);
            PlatformTenantService.CreateTenantReq req = new PlatformTenantService.CreateTenantReq(
                    "demo", "演示婚纱摄影", "WEDDING_PHOTO", "张经理", "13800000000",
                    planId, false, "admin", "admin123");
            tenantService.create(req);
            log.info("已初始化演示租户：demo / admin / admin123");
        }
    }
}
