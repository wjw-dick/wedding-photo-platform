package com.wedding.tenant.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_audit")
public class SysAudit {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long storeId;
    private Long operatorId;
    private String operatorName;
    private String module;
    private String action;
    private String targetType;
    private String targetId;
    private String detail;
    private String result;
    private String ip;
    private Long costMs;
    private LocalDateTime createdAt;
}
