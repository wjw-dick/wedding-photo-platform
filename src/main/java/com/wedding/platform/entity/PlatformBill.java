package com.wedding.platform.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("platform_bill")
public class PlatformBill {
    @TableId(type = IdType.AUTO)
    private Long billId;
    private Long tenantId;
    private String billPeriod;
    private String itemName;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal amount;
    private String status;
    private LocalDateTime paidAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
}
