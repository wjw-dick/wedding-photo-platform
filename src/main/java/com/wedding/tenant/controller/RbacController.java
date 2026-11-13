package com.wedding.tenant.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wedding.common.api.BizException;
import com.wedding.common.api.PageResult;
import com.wedding.common.api.R;
import com.wedding.common.audit.Audited;
import com.wedding.common.context.TenantContext;
import com.wedding.common.security.QuotaService;
import com.wedding.common.security.RequirePermission;
import com.wedding.tenant.entity.SysRole;
import com.wedding.tenant.entity.SysUser;
import com.wedding.tenant.mapper.SysRoleMapper;
import com.wedding.tenant.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/tenant/rbac")
@RequiredArgsConstructor
public class RbacController {
    private final SysRoleMapper roleMapper;
    private final SysUserMapper userMapper;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final QuotaService quotaService;

    @GetMapping("/roles")
    @RequirePermission("system:view")
    public R<List<Map<String, Object>>> roles() {
        return R.ok(jdbc.queryForList("""
                SELECT r.*, (SELECT COUNT(*) FROM sys_user_role ur WHERE ur.role_id=r.id AND ur.tenant_id=r.tenant_id) AS user_count
                FROM sys_role r WHERE r.tenant_id=? AND r.deleted=0 ORDER BY r.id
                """, TenantContext.requireTenantId()));
    }

    @PostMapping("/roles")
    @RequirePermission("system:edit")
    @Audited(module = "rbac", action = "createRole", targetType = "role")
    public R<SysRole> createRole(@RequestBody SysRole role) {
        role.setId(null);
        role.setDataScope(role.getDataScope() == null ? "ALL" : role.getDataScope());
        roleMapper.insert(role);
        return R.ok(role);
    }

    @PutMapping("/roles/{id}")
    @RequirePermission("system:edit")
    @Audited(module = "rbac", action = "updateRole", targetType = "role")
    public R<Void> updateRole(@PathVariable Long id, @RequestBody SysRole role) {
        SysRole exists = roleMapper.selectById(id);
        if (exists == null) throw BizException.notFound("角色不存在");
        role.setId(id);
        roleMapper.updateById(role);
        return R.ok();
    }

    @DeleteMapping("/roles/{id}")
    @RequirePermission("system:edit")
    @Audited(module = "rbac", action = "deleteRole", targetType = "role")
    public R<Void> deleteRole(@PathVariable Long id) {
        Long used = jdbc.queryForObject("SELECT COUNT(*) FROM sys_user_role WHERE role_id=?", Long.class, id);
        if (used != null && used > 0) throw new BizException("角色已分配账号，不能删除");
        roleMapper.deleteById(id);
        return R.ok();
    }

    @GetMapping("/roles/{id}/menus")
    @RequirePermission("system:view")
    public R<List<Long>> roleMenus(@PathVariable Long id) {
        return R.ok(jdbc.queryForList("SELECT menu_id FROM sys_role_menu WHERE tenant_id=? AND role_id=?",
                Long.class, TenantContext.requireTenantId(), id));
    }

    @PutMapping("/roles/{id}/menus")
    @RequirePermission("system:edit")
    @Audited(module = "rbac", action = "assignMenus", targetType = "role")
    @Transactional
    public R<Void> assignMenus(@PathVariable Long id, @RequestBody List<Long> menuIds) {
        Long tid = TenantContext.requireTenantId();
        jdbc.update("DELETE FROM sys_role_menu WHERE tenant_id=? AND role_id=?", tid, id);
        for (Long menuId : menuIds) {
            jdbc.update("INSERT INTO sys_role_menu(tenant_id, role_id, menu_id) VALUES (?,?,?)", tid, id, menuId);
        }
        return R.ok();
    }

    @GetMapping("/menus/tree")
    @RequirePermission("system:view")
    public R<List<Map<String, Object>>> menuTree() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_no, visible
                FROM sys_menu WHERE tenant_id=? AND deleted=0 ORDER BY sort_no, id
                """, TenantContext.requireTenantId());
        return R.ok(buildTree(rows, 0L));
    }

    @GetMapping("/users")
    @RequirePermission("system:view")
    public R<PageResult<Map<String, Object>>> users(@RequestParam(required = false) String keyword,
                                                    @RequestParam(defaultValue = "1") long page,
                                                    @RequestParam(defaultValue = "20") long size) {
        Long tid = TenantContext.requireTenantId();
        String where = " WHERE u.tenant_id=? AND u.deleted=0";
        List<Object> args = new ArrayList<>();
        args.add(tid);
        if (keyword != null && !keyword.isBlank()) {
            where += " AND (u.username LIKE ? OR u.real_name LIKE ? OR u.phone LIKE ?)";
            args.add("%" + keyword + "%");
            args.add("%" + keyword + "%");
            args.add("%" + keyword + "%");
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM sys_user u" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add((page - 1) * size);
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT u.id, u.username, u.real_name, u.phone, u.email, u.user_type, u.status,
                       u.store_id, s.store_name, u.last_login_at, u.created_at,
                       (SELECT GROUP_CONCAT(r.role_name) FROM sys_user_role ur JOIN sys_role r ON r.id=ur.role_id
                        WHERE ur.user_id=u.id AND ur.tenant_id=u.tenant_id) AS roles
                FROM sys_user u LEFT JOIN sys_store s ON s.id=u.store_id AND s.tenant_id=u.tenant_id
                """ + where + " ORDER BY u.id DESC LIMIT ? OFFSET ?", pageArgs.toArray());
        return R.ok(PageResult.of(total == null ? 0 : total, page, size, rows));
    }

    @PostMapping("/users")
    @RequirePermission("system:edit")
    @Audited(module = "rbac", action = "createUser", targetType = "user")
    @Transactional
    public R<Map<String, Object>> createUser(@RequestBody CreateUserReq req) {
        quotaService.checkSeat(TenantContext.requireTenantId(), 1);
        Long dup = jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE tenant_id=? AND username=? AND deleted=0",
                Long.class, TenantContext.requireTenantId(), req.username());
        if (dup != null && dup > 0) throw new BizException("账号已存在");
        SysUser user = new SysUser();
        user.setUsername(req.username());
        user.setPassword(passwordEncoder.encode(req.password() == null ? "Wp@123456" : req.password()));
        user.setRealName(req.realName());
        user.setPhone(req.phone());
        user.setEmail(req.email());
        user.setStoreId(req.storeId());
        user.setUserType(req.userType() == null ? "STAFF" : req.userType());
        user.setStatus(1);
        userMapper.insert(user);
        if (req.roleIds() != null) {
            for (Long roleId : req.roleIds()) {
                jdbc.update("INSERT INTO sys_user_role(tenant_id,user_id,role_id) VALUES (?,?,?)",
                        TenantContext.requireTenantId(), user.getId(), roleId);
            }
        }
        return R.ok(Map.of("id", user.getId(), "initialPassword", req.password() == null ? "Wp@123456" : req.password()));
    }

    @PutMapping("/users/{id}")
    @RequirePermission("system:edit")
    @Audited(module = "rbac", action = "updateUser", targetType = "user")
    public R<Void> updateUser(@PathVariable Long id, @RequestBody SysUser body) {
        SysUser user = userMapper.selectById(id);
        if (user == null) throw BizException.notFound("账号不存在");
        if (body.getRealName() != null) user.setRealName(body.getRealName());
        if (body.getPhone() != null) user.setPhone(body.getPhone());
        if (body.getEmail() != null) user.setEmail(body.getEmail());
        if (body.getStoreId() != null) user.setStoreId(body.getStoreId());
        if (body.getUserType() != null) user.setUserType(body.getUserType());
        if (body.getStatus() != null) user.setStatus(body.getStatus());
        userMapper.updateById(user);
        return R.ok();
    }

    @PostMapping("/users/{id}/reset-password")
    @RequirePermission("system:edit")
    @Audited(module = "rbac", action = "resetPassword", targetType = "user")
    public R<Map<String, Object>> resetPassword(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        String pwd = body == null || body.get("password") == null ? "Wp@123456" : body.get("password");
        jdbc.update("UPDATE sys_user SET password=? WHERE tenant_id=? AND id=?",
                passwordEncoder.encode(pwd), TenantContext.requireTenantId(), id);
        return R.ok(Map.of("initialPassword", pwd));
    }

    @PutMapping("/users/{id}/roles")
    @RequirePermission("system:edit")
    @Audited(module = "rbac", action = "assignRoles", targetType = "user")
    public R<Void> assignRoles(@PathVariable Long id, @RequestBody List<Long> roleIds) {
        Long tid = TenantContext.requireTenantId();
        jdbc.update("DELETE FROM sys_user_role WHERE tenant_id=? AND user_id=?", tid, id);
        for (Long roleId : roleIds) {
            jdbc.update("INSERT INTO sys_user_role(tenant_id,user_id,role_id) VALUES (?,?,?)", tid, id, roleId);
        }
        return R.ok();
    }

    private List<Map<String, Object>> buildTree(List<Map<String, Object>> rows, Long parentId) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            if (parentId.equals(((Number) row.get("parent_id")).longValue())) {
                Map<String, Object> node = new LinkedHashMap<>(row);
                node.put("children", buildTree(rows, ((Number) row.get("id")).longValue()));
                result.add(node);
            }
        }
        return result;
    }

    public record CreateUserReq(String username, String password, String realName, String phone,
                                String email, Long storeId, String userType, List<Long> roleIds) {
    }
}
