package com.wedding.tenant.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_i18n")
public class SysI18n {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String langCode;
    private String i18nKey;
    private String i18nValue;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
