package com.wedding.tenant.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_language")
public class SysLanguage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String langCode;
    private String langName;
    private Integer isDefault;
    private Integer status;
    private Integer sortNo;
    private LocalDateTime createdAt;
}
