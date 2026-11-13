package com.wedding.tenant.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_menu_config")
public class SysMenuConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String endType;
    private String configName;
    private Integer published;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
