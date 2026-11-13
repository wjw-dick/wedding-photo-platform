package com.wedding.tenant.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("sys_store")
public class SysStore {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String storeName;
    private String address;
    private String phone;
    private String businessHours;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private Integer capacity;
    private String commissionRule;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
}
