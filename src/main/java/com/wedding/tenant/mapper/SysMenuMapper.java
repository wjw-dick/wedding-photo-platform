package com.wedding.tenant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wedding.tenant.entity.SysMenu;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysMenuMapper extends BaseMapper<SysMenu> {

    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT DISTINCT m.permission FROM sys_menu m
            JOIN sys_role_menu rm ON rm.menu_id = m.id AND rm.tenant_id = m.tenant_id
            JOIN sys_user_role ur ON ur.role_id = rm.role_id AND ur.tenant_id = m.tenant_id
            WHERE m.tenant_id = #{tenantId} AND ur.user_id = #{userId}
              AND m.deleted = 0 AND m.permission IS NOT NULL AND m.permission <> ''
            """)
    List<String> selectPermissions(@Param("tenantId") Long tenantId, @Param("userId") Long userId);

    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT DISTINCT r.data_scope FROM sys_role r
            JOIN sys_user_role ur ON ur.role_id = r.id AND ur.tenant_id = r.tenant_id
            WHERE r.tenant_id = #{tenantId} AND ur.user_id = #{userId} AND r.deleted = 0
            """)
    List<String> selectDataScopes(@Param("tenantId") Long tenantId, @Param("userId") Long userId);

    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT DISTINCT m.* FROM sys_menu m
            JOIN sys_role_menu rm ON rm.menu_id = m.id AND rm.tenant_id = m.tenant_id
            JOIN sys_user_role ur ON ur.role_id = rm.role_id AND ur.tenant_id = m.tenant_id
            WHERE m.tenant_id = #{tenantId} AND ur.user_id = #{userId} AND m.deleted = 0
            ORDER BY m.sort_no, m.id
            """)
    List<SysMenu> selectByUserId(@Param("tenantId") Long tenantId, @Param("userId") Long userId);
}
