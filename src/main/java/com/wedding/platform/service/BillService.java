package com.wedding.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wedding.common.api.PageResult;
import com.wedding.platform.entity.PlatformBill;
import com.wedding.platform.entity.PlatformPlan;
import com.wedding.platform.entity.PlatformTenant;
import com.wedding.platform.mapper.PlatformBillMapper;
import com.wedding.platform.mapper.PlatformPlanMapper;
import com.wedding.platform.mapper.PlatformTenantMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BillService {
    private final PlatformBillMapper billMapper;
    private final PlatformTenantMapper tenantMapper;
    private final PlatformPlanMapper planMapper;
    private final JdbcTemplate jdbc;

    public PageResult<PlatformBill> list(Long tenantId, String period, String status, long page, long size) {
        Page<PlatformBill> p = billMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<PlatformBill>()
                        .eq(tenantId != null, PlatformBill::getTenantId, tenantId)
                        .eq(period != null && !period.isBlank(), PlatformBill::getBillPeriod, period)
                        .eq(status != null && !status.isBlank(), PlatformBill::getStatus, status)
                        .orderByDesc(PlatformBill::getCreatedAt));
        return PageResult.of(p.getTotal(), page, size, p.getRecords());
    }

    /** 按账期生成席位账单：套餐基础费 + 超出席位加购，重复生成幂等。 */
    public int generate(String period) {
        List<PlatformTenant> tenants = tenantMapper.selectList(new LambdaQueryWrapper<PlatformTenant>()
                .in(PlatformTenant::getStatus, List.of("ACTIVE", "TRIAL", "OVERDUE")));
        int created = 0;
        for (PlatformTenant t : tenants) {
            Long exists = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM platform_bill WHERE tenant_id=? AND bill_period=? AND item_name='套餐与席位'",
                    Long.class, t.getTenantId(), period);
            if (exists != null && exists > 0) continue;
            PlatformPlan plan = t.getPlanId() == null ? null : planMapper.selectById(t.getPlanId());
            BigDecimal base = plan == null ? BigDecimal.ZERO : plan.getPrice();
            long seats = jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE tenant_id=? AND deleted=0",
                    Long.class, t.getTenantId());
            int extra = (int) Math.max(0, seats - t.getSeatLimit());
            BigDecimal perSeat = plan == null || plan.getSeatLimit() == null || plan.getSeatLimit() == 0
                    ? BigDecimal.ZERO
                    : plan.getPrice().divide(BigDecimal.valueOf(plan.getSeatLimit()), 2, RoundingMode.HALF_UP);
            BigDecimal amount = base.add(perSeat.multiply(BigDecimal.valueOf(extra)));

            PlatformBill bill = new PlatformBill();
            bill.setTenantId(t.getTenantId());
            bill.setBillPeriod(period);
            bill.setItemName("套餐与席位");
            bill.setQuantity((int) seats);
            bill.setUnitPrice(perSeat);
            bill.setAmount(amount);
            bill.setStatus("UNPAID");
            billMapper.insert(bill);
            created++;
        }
        return created;
    }

    public void markPaid(Long billId) {
        PlatformBill bill = billMapper.selectById(billId);
        if (bill == null) return;
        bill.setStatus("PAID");
        bill.setPaidAt(LocalDateTime.now());
        billMapper.updateById(bill);
    }

    public void markOverdue() {
        jdbc.update("UPDATE platform_bill SET status='OVERDUE' WHERE status='UNPAID' AND created_at < ?",
                LocalDateTime.now().minusDays(15));
    }

    public String exportCsv(String period) {
        List<PlatformBill> list = billMapper.selectList(new LambdaQueryWrapper<PlatformBill>()
                .eq(period != null && !period.isBlank(), PlatformBill::getBillPeriod, period)
                .orderByAsc(PlatformBill::getTenantId));
        StringBuilder sb = new StringBuilder("billId,tenantId,period,item,quantity,unitPrice,amount,status\n");
        for (PlatformBill b : list) {
            sb.append(b.getBillId()).append(',').append(b.getTenantId()).append(',')
                    .append(b.getBillPeriod()).append(',').append(b.getItemName()).append(',')
                    .append(b.getQuantity()).append(',').append(b.getUnitPrice()).append(',')
                    .append(b.getAmount()).append(',').append(b.getStatus()).append('\n');
        }
        return sb.toString();
    }

    public String currentPeriod() {
        return YearMonth.from(LocalDate.now()).toString();
    }
}
