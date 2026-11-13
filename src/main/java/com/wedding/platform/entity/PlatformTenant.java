package com.wedding.platform.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("platform_tenant")
public class PlatformTenant {
    @TableId(type = IdType.AUTO)
    private Long tenantId;
    private String tenantCode;
    private String tenantName;
    private String industry;
    private String contactName;
    private String contactPhone;
    private Long planId;
    private String status;
    private LocalDateTime trialEndAt;
    private LocalDateTime expireAt;
    private Integer seatLimit;
    private Integer mpAccountLimit;
    private Integer storageQuotaGb;
    private Integer smsQuota;
    private Long apiQuota;
    private String overLimitPolicy;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
}
