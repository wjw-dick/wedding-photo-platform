package com.wedding.tenant.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_menu_item")
public class SysMenuItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long configId;
    private Long parentId;
    private String title;
    private String icon;
    private String itemType;
    private String path;
    private Integer sortNo;
    private Integer visible;
    private String extraJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
