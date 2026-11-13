package com.wedding.platform.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("platform_plan")
public class PlatformPlan {
    @TableId(type = IdType.AUTO)
    private Long planId;
    private String planName;
    private BigDecimal price;
    private String billingCycle;
    private Integer seatLimit;
    private Integer mpAccountLimit;
    private Integer storageQuotaGb;
    private Integer smsQuota;
    private Long apiQuota;
    private Integer trialDays;
    private Integer allowTrial;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
}
