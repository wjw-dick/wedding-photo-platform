package com.wedding.tenant.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_param")
public class SysParam {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String paramGroup;
    private String paramKey;
    private String paramValue;
    private String valueType;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
}
