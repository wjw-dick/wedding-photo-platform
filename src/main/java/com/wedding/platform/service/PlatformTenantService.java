package com.wedding.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wedding.common.api.BizException;
import com.wedding.common.api.PageResult;
import com.wedding.common.audit.Audited;
import com.wedding.common.context.TenantContext;
import com.wedding.common.security.PermissionService;
import com.wedding.common.security.TenantGuard;
import com.wedding.platform.entity.PlatformPlan;
import com.wedding.platform.entity.PlatformTenant;
import com.wedding.platform.mapper.PlatformPlanMapper;
import com.wedding.platform.mapper.PlatformTenantMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PlatformTenantService {
    private final PlatformTenantMapper tenantMapper;
    private final PlatformPlanMapper planMapper;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redis;
    private final TenantGuard tenantGuard;
    private final PermissionService permissionService;

    public PageResult<Map<String, Object>> list(String keyword, String status, long page, long size) {
        Page<PlatformTenant> p = tenantMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<PlatformTenant>()
                        .and(keyword != null && !keyword.isBlank(), w -> w
                                .like(PlatformTenant::getTenantName, keyword)
                                .or().like(PlatformTenant::getTenantCode, keyword))
                        .eq(status != null && !status.isBlank(), PlatformTenant::getStatus, status)
                        .orderByDesc(PlatformTenant::getCreatedAt));
        List<Map<String, Object>> records = p.getRecords().stream().map(t -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("tenantId", t.getTenantId());
            m.put("tenantCode", t.getTenantCode());
            m.put("tenantName", t.getTenantName());
            m.put("industry", t.getIndustry());
            m.put("planId", t.getPlanId());
            m.put("planName", planName(t.getPlanId()));
            m.put("status", t.getStatus());
            m.put("trialEndAt", t.getTrialEndAt());
            m.put("expireAt", t.getExpireAt());
            m.put("seatLimit", t.getSeatLimit());
            m.put("usedSeat", count("SELECT COUNT(*) FROM sys_user WHERE tenant_id=? AND deleted=0", t.getTenantId()));
            m.put("mpAccountLimit", t.getMpAccountLimit());
            m.put("usedMp", count("SELECT COUNT(*) FROM mp_account WHERE tenant_id=? AND deleted=0", t.getTenantId()));
            m.put("storageQuotaGb", t.getStorageQuotaGb());
            m.put("createdAt", t.getCreatedAt());
            return m;
        }).toList();
        return PageResult.of(p.getTotal(), page, size, records);
    }

    public Map<String, Object> detail(Long tenantId) {
        PlatformTenant t = tenantMapper.selectById(tenantId);
        if (t == null) throw BizException.notFound("租户不存在");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tenant", t);
        m.put("plan", t.getPlanId() == null ? null : planMapper.selectById(t.getPlanId()));
        m.put("usage", usage(tenantId));
        m.put("admins", jdbc.queryForList("""
                SELECT u.id, u.username, u.real_name, u.phone, a.is_master
                FROM platform_tenant_admin a JOIN sys_user u ON u.id=a.user_id AND u.tenant_id=a.tenant_id
                WHERE a.tenant_id=?
                """, tenantId));
        return m;
    }

    @Transactional
    @Audited(module = "tenant", action = "create", targetType = "tenant")
    public Map<String, Object> create(CreateTenantReq req) {
        Long exists = jdbc.queryForObject("SELECT COUNT(*) FROM platform_tenant WHERE tenant_code=?",
                Long.class, req.tenantCode());
        if (exists != null && exists > 0) {
            throw new BizException("租户编码已存在");
        }
        PlatformPlan plan = planMapper.selectById(req.planId());
        if (plan == null) throw new BizException("套餐不存在");
        boolean trial = req.trial() == null || req.trial();
        PlatformTenant t = new PlatformTenant();
        t.setTenantCode(req.tenantCode());
        t.setTenantName(req.tenantName());
        t.setIndustry(req.industry() == null ? "WEDDING_PHOTO" : req.industry());
        t.setContactName(req.contactName());
        t.setContactPhone(req.contactPhone());
        t.setPlanId(plan.getPlanId());
        t.setStatus(trial ? "TRIAL" : "ACTIVE");
        t.setTrialEndAt(trial ? LocalDateTime.now().plusDays(plan.getTrialDays()) : null);
        t.setExpireAt(LocalDateTime.now().plusYears(1));
        t.setSeatLimit(plan.getSeatLimit());
        t.setMpAccountLimit(plan.getMpAccountLimit());
        t.setStorageQuotaGb(plan.getStorageQuotaGb());
        t.setSmsQuota(plan.getSmsQuota());
        t.setApiQuota(plan.getApiQuota());
        t.setOverLimitPolicy("BLOCK_WRITE");
        tenantMapper.insert(t);

        String rawPassword = req.adminPassword() == null || req.adminPassword().isBlank()
                ? "Wp@" + UUID.randomUUID().toString().replace("-", "").substring(0, 8)
                : req.adminPassword();
        TenantContext.set(t.getTenantId(), t.getTenantCode());
        initialize(t, req.adminUsername(), rawPassword);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tenantId", t.getTenantId());
        result.put("tenantCode", t.getTenantCode());
        result.put("adminUsername", req.adminUsername());
        result.put("initialPassword", rawPassword);
        result.put("notice", "初始密码仅本次展示，请立即修改");
        return result;
    }

    /** 开通初始化：默认角色、菜单、参数、门店、语言、菜单配置、消息通道、存储目录。 */
    private void initialize(PlatformTenant tenant, String adminUsername, String rawPassword) {
        Long tid = tenant.getTenantId();
        Long roleId = insertKeys("INSERT INTO sys_role(tenant_id, role_code, role_name, data_scope, remark) VALUES (?,?,?,?,?)",
                tid, "TENANT_ADMIN", "租户管理员", "ALL", "默认角色");
        jdbc.update("INSERT INTO sys_role(tenant_id, role_code, role_name, data_scope, remark) VALUES (?,?,?,?,?)",
                tid, "STORE_MANAGER", "店长", "STORE", "仅本门店数据");
        jdbc.update("INSERT INTO sys_role(tenant_id, role_code, role_name, data_scope, remark) VALUES (?,?,?,?,?)",
                tid, "SALES", "门市", "SELF", "本人客户");
        jdbc.update("INSERT INTO sys_role(tenant_id, role_code, role_name, data_scope, remark) VALUES (?,?,?,?,?)",
                tid, "PHOTOGRAPHER", "摄影师", "SELF", "");
        jdbc.update("INSERT INTO sys_role(tenant_id, role_code, role_name, data_scope, remark) VALUES (?,?,?,?,?)",
                tid, "EDITOR", "修片师", "SELF", "");
        jdbc.update("INSERT INTO sys_role(tenant_id, role_code, role_name, data_scope, remark) VALUES (?,?,?,?,?)",
                tid, "FINANCE", "财务", "ALL", "");

        Long storeId = insertKeys("INSERT INTO sys_store(tenant_id, store_name, address, phone, capacity) VALUES (?,?,?,?,?)",
                tid, "总部门店", "", "", 4);

        Long adminId = insertKeys("""
                INSERT INTO sys_user(tenant_id, store_id, username, password, real_name, phone, user_type, status)
                VALUES (?,?,?,?,?,?,?,1)
                """, tid, storeId, adminUsername, passwordEncoder.encode(rawPassword), "管理员", "", "TENANT_ADMIN");
        jdbc.update("INSERT INTO sys_user_role(tenant_id, user_id, role_id) VALUES (?,?,?)", tid, adminId, roleId);
        jdbc.update("INSERT INTO platform_tenant_admin(tenant_id, user_id, is_master) VALUES (?,?,1)", tid, adminId);

        seedMenus(tid, roleId);

        String[][] params = {
                {"site", "site.name", tenant.getTenantName(), "STRING"},
                {"site", "site.icp", "", "STRING"},
                {"site", "site.servicePhone", "", "STRING"},
                {"site", "site.businessHours", "09:00-18:00", "STRING"},
                {"trade", "deposit.ratio", "30", "NUMBER"},
                {"trade", "balance.node", "SHOOT_DAY", "STRING"},
                {"trade", "extraPhoto.price", "50", "NUMBER"},
                {"privacy", "retain.days", "90", "NUMBER"},
                {"privacy", "watermark.text", tenant.getTenantName() + " 客片预览", "STRING"},
                {"storage", "storage.default", "LOCAL", "STRING"}
        };
        for (String[] p : params) {
            jdbc.update("INSERT INTO sys_param(tenant_id, param_group, param_key, param_value, value_type) VALUES (?,?,?,?,?)",
                    tid, p[0], p[1], p[2], p[3]);
        }

        jdbc.update("INSERT INTO sys_language(tenant_id, lang_code, lang_name, is_default, status, sort_no) VALUES (?,?,?,?,?,?)",
                tid, "zh", "简体中文", 1, 1, 1);
        jdbc.update("INSERT INTO sys_language(tenant_id, lang_code, lang_name, is_default, status, sort_no) VALUES (?,?,?,?,?,?)",
                tid, "en", "English", 0, 1, 2);

        String[][] ends = {{"PC_ADMIN", "后台导航"}, {"PC_OFFICIAL", "官网导航"}, {"MP", "公众号菜单"},
                {"MINI_TABBAR", "小程序底部"}, {"H5", "H5 底部"}};
        Map<String, Long> configIds = new LinkedHashMap<>();
        for (String[] e : ends) {
            configIds.put(e[0], insertKeys(
                    "INSERT INTO sys_menu_config(tenant_id, end_type, config_name, published) VALUES (?,?,?,1)",
                    tid, e[0], e[1]));
        }
        jdbc.update("INSERT INTO sys_menu_item(tenant_id, config_id, title, item_type, path, sort_no) VALUES (?,?,?,?,?,?)",
                tid, configIds.get("MINI_TABBAR"), "首页", "VIEW", "/pages/index/index", 1);
        for (String ch : List.of("SITE", "MP_TEMPLATE", "MINI_SUBSCRIBE", "APP_PUSH")) {
            jdbc.update("INSERT INTO msg_channel(tenant_id, channel, enabled) VALUES (?,?,?)", tid, ch, 0);
        }
        for (String sms : List.of("ALIYUN", "TENCENT", "HUAWEI", "JD", "AWS_SNS", "LOCAL_LOG")) {
            jdbc.update("INSERT INTO sms_channel(tenant_id, provider, enabled) VALUES (?,?,?)", tid, sms, sms.equals("LOCAL_LOG") ? 1 : 0);
        }
        jdbc.update("INSERT INTO platform_tenant_quota_usage(tenant_id, stat_date, used_seat) VALUES (?,?,1)",
                tid, LocalDate.now());
        // 演示数据：一个上架套系与资源，便于开通后立即体验
        jdbc.update("""
                INSERT INTO pkg_package(tenant_id, package_no, name, price, deposit, finished_count, retouch_count,
                    delivery_days, status, shoot_duration, dress_count, scene_count)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                """, tid, "PKG-001", "轻奢婚纱套系", 6999, 2100, 120, 30, 30, "ON", "8小时", 4, 3);
        jdbc.update("INSERT INTO pkg_addon(tenant_id, addon_code, addon_name, addon_type, unit, base_price) VALUES (?,?,?,?,?,?)",
                tid, "ADD-PHOTO", "加片", "EXTRA_PHOTO", "张", 50);
        jdbc.update("INSERT INTO sch_resource(tenant_id, store_id, resource_type, resource_name, capacity) VALUES (?,?,?,?,1)",
                tid, storeId, "PHOTOGRAPHER", "首席摄影师");
        jdbc.update("INSERT INTO sch_resource(tenant_id, store_id, resource_type, resource_name, capacity) VALUES (?,?,?,?,1)",
                tid, storeId, "MAKEUP", "首席化妆师");
        jdbc.update("""
                INSERT INTO contract_template(tenant_id, template_name, content, status)
                VALUES (?,?,?,1)
                """, tid, "婚纱摄影服务合同（默认）", """
                甲方（客户）：{{customerName}}
                乙方：{{tenantName}}
                拍摄日期：{{shootDate}}
                套系：{{packageName}}
                合同金额：{{totalAmount}} 元
                定金：{{depositAmount}} 元

                一、乙方按约定提供拍摄、精修与交付服务。
                二、甲方应按约定支付定金与尾款。
                三、改期与退订按门店公示规则执行。
                四、客片肖像使用须另行授权。
                五、客片保留期 90 天，逾期可申请延期一次。
                """);
    }

    private void seedMenus(Long tid, Long adminRoleId) {
        String[][] top = {
                {"工作台", "dashboard:read", "/dashboard", "Odometer"},
                {"订单中心", "order:view", "/order", "Tickets"},
                {"生产交付", "production:view", "/production", "Camera"},
                {"客户运营", "member:view", "/member", "User"},
                {"产品与档期", "package:view", "/catalog", "Goods"},
                {"渠道与供应商", "channel:view", "/channel", "Connection"},
                {"内容运营", "cms:view", "/cms", "Document"},
                {"公众号", "mp:view", "/mp", "ChatDotRound"},
                {"系统设置", "system:view", "/system", "Setting"}
        };
        Map<String, Long> topIds = new LinkedHashMap<>();
        int sort = 1;
        for (String[] m : top) {
            Long id = insertKeys("INSERT INTO sys_menu(tenant_id,parent_id,menu_name,menu_type,path,permission,icon,sort_no,visible) VALUES (?,?,?,?,?,?,?,?,1)",
                    tid, 0L, m[0], "DIR", m[2], m[1], m[3], sort++);
            topIds.put(m[0], id);
            jdbc.update("INSERT INTO sys_role_menu(tenant_id, role_id, menu_id) VALUES (?,?,?)", tid, adminRoleId, id);
        }
        String[][] leaves = {
                {"订单中心", "订单管理", "/order/list", "order:*", "Tickets"},
                {"订单中心", "档期日历", "/schedule/calendar", "schedule:*", "Calendar"},
                {"订单中心", "收款与退款", "/order/payment", "order:*", "Money"},
                {"生产交付", "拍摄任务", "/production/shoot", "production:*", "Camera"},
                {"生产交付", "选片管理", "/production/selection", "selection:*", "Picture"},
                {"生产交付", "修片任务", "/production/retouch", "retouch:*", "MagicStick"},
                {"生产交付", "制作与交付", "/production/delivery", "delivery:*", "Van"},
                {"生产交付", "礼服租赁", "/production/dress", "dress:*", "Handbag"},
                {"客户运营", "会员管理", "/member/list", "member:*", "User"},
                {"客户运营", "线索 CRM", "/member/lead", "lead:*", "Phone"},
                {"客户运营", "优惠券", "/member/coupon", "coupon:*", "Discount"},
                {"产品与档期", "门店与影棚", "/catalog/store", "store:*", "OfficeBuilding"},
                {"产品与档期", "套系商品", "/catalog/package", "package:*", "Goods"},
                {"产品与档期", "加购项", "/catalog/addon", "package:*", "Plus"},
                {"渠道与供应商", "渠道商", "/channel/list", "channel:*", "Connection"},
                {"渠道与供应商", "供应商", "/supplier/list", "supplier:*", "Box"},
                {"渠道与供应商", "员工与提成", "/employee/list", "employee:*", "Avatar"},
                {"渠道与供应商", "统计报表", "/report/index", "report:*", "DataLine"},
                {"内容运营", "官网 CMS", "/cms/index", "cms:*", "Document"},
                {"内容运营", "多端入口", "/entry/index", "entry:*", "Link"},
                {"公众号", "公众号管理", "/mp/account", "mp:*", "ChatDotRound"},
                {"系统设置", "系统参数", "/system/param", "system:*", "SetUp"},
                {"系统设置", "角色权限", "/system/role", "system:*", "Lock"},
                {"系统设置", "账号管理", "/system/user", "system:*", "UserFilled"},
                {"系统设置", "菜单可视化", "/system/menu-designer", "system:*", "Grid"},
                {"系统设置", "对象存储", "/system/storage", "system:*", "FolderOpened"},
                {"系统设置", "消息配置", "/system/message", "system:*", "Bell"},
                {"系统设置", "SSO 配置", "/system/sso", "system:*", "Key"},
                {"系统设置", "审计日志", "/system/audit", "system:audit", "List"},
                {"系统设置", "多语言", "/system/language", "system:*", "ChatLineSquare"}
        };
        for (String[] m : leaves) {
            Long id = insertKeys("INSERT INTO sys_menu(tenant_id,parent_id,menu_name,menu_type,path,permission,icon,sort_no,visible) VALUES (?,?,?,?,?,?,?,?,1)",
                    tid, topIds.get(m[0]), m[1], "MENU", m[2], m[3], m[4], sort++);
            jdbc.update("INSERT INTO sys_role_menu(tenant_id, role_id, menu_id) VALUES (?,?,?)", tid, adminRoleId, id);
        }
    }

    @Audited(module = "tenant", action = "status", targetType = "tenant")
    public void changeStatus(Long tenantId, String status) {
        PlatformTenant t = tenantMapper.selectById(tenantId);
        if (t == null) throw BizException.notFound("租户不存在");
        if (!List.of("TRIAL", "ACTIVE", "OVERDUE", "SUSPENDED", "EXPIRED").contains(status)) {
            throw new BizException("非法状态：" + status);
        }
        t.setStatus(status);
        tenantMapper.updateById(t);
        evictTenant(tenantId);
    }

    @Audited(module = "tenant", action = "renew", targetType = "tenant")
    public void renew(Long tenantId, LocalDateTime expireAt) {
        PlatformTenant t = tenantMapper.selectById(tenantId);
        if (t == null) throw BizException.notFound("租户不存在");
        t.setExpireAt(expireAt);
        t.setStatus("ACTIVE");
        tenantMapper.updateById(t);
        evictTenant(tenantId);
    }

    @Audited(module = "tenant", action = "quota", targetType = "tenant")
    public void updateQuota(Long tenantId, Integer seatLimit, Integer mpLimit, Integer storageGb,
                            Integer smsQuota, Long apiQuota) {
        PlatformTenant t = tenantMapper.selectById(tenantId);
        if (t == null) throw BizException.notFound("租户不存在");
        if (seatLimit != null) t.setSeatLimit(seatLimit);
        if (mpLimit != null) t.setMpAccountLimit(mpLimit);
        if (storageGb != null) t.setStorageQuotaGb(storageGb);
        if (smsQuota != null) t.setSmsQuota(smsQuota);
        if (apiQuota != null) t.setApiQuota(apiQuota);
        tenantMapper.updateById(t);
        evictTenant(tenantId);
    }

    @Audited(module = "tenant", action = "delete", targetType = "tenant")
    public void delete(Long tenantId) {
        tenantMapper.deleteById(tenantId);
        evictTenant(tenantId);
    }

    public Map<String, Object> usage(Long tenantId) {
        long seats = count("SELECT COUNT(*) FROM sys_user WHERE tenant_id=? AND deleted=0", tenantId);
        long mp = count("SELECT COUNT(*) FROM mp_account WHERE tenant_id=? AND deleted=0", tenantId);
        long storage = count("SELECT COALESCE(SUM(size_bytes),0) FROM media_file WHERE tenant_id=?", tenantId);
        PlatformTenant t = tenantMapper.selectById(tenantId);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("usedSeat", seats);
        m.put("seatLimit", t == null ? 0 : t.getSeatLimit());
        m.put("usedMp", mp);
        m.put("mpLimit", t == null ? 0 : t.getMpAccountLimit());
        m.put("usedStorageBytes", storage);
        m.put("storageLimitBytes", (t == null ? 0L : t.getStorageQuotaGb().longValue()) * 1024 * 1024 * 1024);
        m.put("smsQuota", t == null ? 0 : t.getSmsQuota());
        m.put("apiQuota", t == null ? 0 : t.getApiQuota());
        return m;
    }

    private void evictTenant(Long tenantId) {
        tenantGuard.evict(tenantId);
        permissionService.evictTenant(tenantId);
        Set<String> keys = redis.keys("tenant:" + tenantId + ":*");
        if (keys != null && !keys.isEmpty()) redis.delete(keys);
    }

    private String planName(Long planId) {
        if (planId == null) return "";
        PlatformPlan p = planMapper.selectById(planId);
        return p == null ? "" : p.getPlanName();
    }

    private long count(String sql, Object... args) {
        Long v = jdbc.queryForObject(sql, Long.class, args);
        return v == null ? 0 : v;
    }

    private Long insertKeys(String sql, Object... args) {
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
            return ps;
        }, kh);
        return Objects.requireNonNull(kh.getKey()).longValue();
    }

    public record CreateTenantReq(String tenantCode, String tenantName, String industry,
                                  String contactName, String contactPhone, Long planId,
                                  Boolean trial, String adminUsername, String adminPassword) {
    }
}
