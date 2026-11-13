package com.wedding.biz.schedule;

import com.wedding.common.api.BizException;
import com.wedding.common.context.TenantContext;
import com.wedding.common.util.BizNo;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

/** 档期：Redis 分布式锁 + 数据库唯一索引 + 事务内二次校验，三道防线防止超卖。 */
@Service
@RequiredArgsConstructor
public class ScheduleService {
    private static final DefaultRedisScript<Long> UNLOCK = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);
    private final JdbcTemplate jdbc;
    private final StringRedisTemplate redis;
    private final BizNo bizNo;
    private final org.springframework.amqp.rabbit.core.RabbitTemplate rabbitTemplate;

    public List<Map<String, Object>> resources(String type) {
        Long tid = TenantContext.requireTenantId();
        StringBuilder sql = new StringBuilder("""
                SELECT r.*, s.store_name, e.real_name AS employee_name
                FROM sch_resource r LEFT JOIN sys_store s ON s.id=r.store_id AND s.tenant_id=r.tenant_id
                LEFT JOIN employee e ON e.id=r.employee_id AND e.tenant_id=r.tenant_id
                WHERE r.tenant_id=? AND r.deleted=0
                """);
        List<Object> args = new ArrayList<>();
        args.add(tid);
        if (type != null && !type.isBlank()) {
            sql.append(" AND r.resource_type=?");
            args.add(type);
        }
        sql.append(" ORDER BY r.resource_type, r.id");
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    public Map<String, Object> calendar(LocalDate from, LocalDate to, String type) {
        Long tid = TenantContext.requireTenantId();
        List<Map<String, Object>> resources = resources(type);
        StringBuilder sql = new StringBuilder("""
                SELECT sl.*, r.resource_name, r.resource_type, s.store_name,
                       o.order_no, o.customer_name, o.status AS order_status
                FROM sch_slot sl
                JOIN sch_resource r ON r.id=sl.resource_id AND r.tenant_id=sl.tenant_id
                LEFT JOIN sys_store s ON s.id=sl.store_id AND s.tenant_id=sl.tenant_id
                LEFT JOIN order_main o ON o.id=sl.order_id AND o.tenant_id=sl.tenant_id
                WHERE sl.tenant_id=? AND sl.slot_date BETWEEN ? AND ?
                """);
        List<Object> args = new ArrayList<>(List.of(tid, from, to));
        if (type != null && !type.isBlank()) {
            sql.append(" AND r.resource_type=?");
            args.add(type);
        }
        sql.append(" ORDER BY sl.slot_date, sl.slot_start, sl.resource_id");
        return Map.of("from", from, "to", to, "resources", resources,
                "slots", jdbc.queryForList(sql.toString(), args.toArray()));
    }

    public Map<String, Object> createSlot(Long resourceId, LocalDate date, LocalTime start,
                                          LocalTime end, Integer capacity) {
        Long tid = TenantContext.requireTenantId();
        Map<String, Object> resource = jdbc.queryForMap(
                "SELECT * FROM sch_resource WHERE tenant_id=? AND id=? AND deleted=0", tid, resourceId);
        try {
            jdbc.update("""
                    INSERT INTO sch_slot(tenant_id, store_id, resource_id, slot_date, slot_start, slot_end, capacity, status)
                    VALUES (?,?,?,?,?,?,?, 'OPEN')
                    """, tid, resource.get("store_id"), resourceId, date, start, end, capacity == null ? 1 : capacity);
        } catch (DuplicateKeyException e) {
            throw new BizException(409, "该资源在该时段已存在档期行");
        }
        return Map.of("ok", true);
    }

    /** 锁档 30 分钟：未付定金由定时任务自动释放。 */
    @Transactional
    public Map<String, Object> lock(Long resourceId, LocalDate date, LocalTime start, LocalTime end,
                                    String orderNo, Long memberId) {
        Long tid = TenantContext.requireTenantId();
        String lockKey = "tenant:" + tid + ":slot:" + date + ":" + resourceId;
        String token = UUID.randomUUID().toString();
        boolean acquired = false;
        for (int i = 0; i < 3 && !acquired; i++) {
            acquired = Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(lockKey, token, Duration.ofSeconds(10)));
            if (!acquired) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        if (!acquired) throw new BizException(429, "该时段正在被抢订，请稍后重试或选择其他时段");
        try {
            Map<String, Object> slot;
            try {
                slot = jdbc.queryForMap("""
                        SELECT * FROM sch_slot WHERE tenant_id=? AND resource_id=? AND slot_date=? AND slot_start=? FOR UPDATE
                        """, tid, resourceId, date, start);
            } catch (org.springframework.dao.EmptyResultDataAccessException e) {
                slot = null;
            }
            if (slot != null) {
                String status = String.valueOf(slot.get("status"));
                int used = ((Number) slot.get("used_count")).intValue();
                int capacity = ((Number) slot.get("capacity")).intValue();
                if ("CLOSED".equals(status) || used >= capacity) {
                    throw new BizException(409, "该时段已约满，请选择其他时段");
                }
                jdbc.update("UPDATE sch_slot SET used_count=used_count+1, status='LOCKED' WHERE id=? AND tenant_id=?",
                        slot.get("id"), tid);
            } else {
                try {
                    jdbc.update("""
                            INSERT INTO sch_slot(tenant_id, store_id, resource_id, slot_date, slot_start, slot_end,
                                                 capacity, used_count, status)
                            SELECT ?, store_id, id, ?, ?, ?, 1, 1, 'LOCKED' FROM sch_resource WHERE tenant_id=? AND id=?
                            """, tid, date, start, end, tid, resourceId);
                } catch (DuplicateKeyException e) {
                    throw new BizException(409, "该时段刚被他人锁定，请选择其他时段");
                }
            }
            LocalDateTime expireAt = LocalDateTime.now().plusMinutes(30);
            String key = bizNo.next("LK");
            jdbc.update("""
                    INSERT INTO sch_lock(tenant_id, lock_key, resource_id, slot_date, slot_start, order_no, member_id,
                                         expire_at, status)
                    VALUES (?,?,?,?,?,?,?,?, 'LOCKED')
                    """, tid, key, resourceId, date, start, orderNo, memberId, expireAt);
            try {
                Map<String, Object> msg = new LinkedHashMap<>();
                msg.put("tenantId", tid);
                msg.put("type", "RELEASE_LOCK");
                msg.put("lockKey", key);
                rabbitTemplate.convertAndSend("", com.wedding.common.config.RabbitConfig.DELAY, msg, m -> {
                    m.getMessageProperties().setExpiration(String.valueOf(30 * 60 * 1000));
                    return m;
                });
            } catch (Exception ignored) {
                // 定时任务兜底释放
            }
            return Map.of("lockKey", key, "expireAt", expireAt, "resourceId", resourceId,
                    "slotDate", date, "slotStart", start);
        } finally {
            redis.execute(UNLOCK, Collections.singletonList(lockKey), token);
        }
    }

    @Transactional
    public void confirm(String lockKey, Long orderId) {
        Long tid = TenantContext.requireTenantId();
        Map<String, Object> lock = jdbc.queryForMap(
                "SELECT * FROM sch_lock WHERE tenant_id=? AND lock_key=? FOR UPDATE", tid, lockKey);
        if ("RELEASED".equals(lock.get("status"))) {
            throw new BizException("锁档已过期，请重新选择时段");
        }
        jdbc.update("UPDATE sch_lock SET status='CONFIRMED', order_no=? WHERE tenant_id=? AND lock_key=?",
                lock.get("order_no"), tid, lockKey);
        jdbc.update("""
                UPDATE sch_slot SET status='BOOKED', order_id=?
                WHERE tenant_id=? AND resource_id=? AND slot_date=? AND slot_start=?
                """, orderId, tid, lock.get("resource_id"), lock.get("slot_date"), lock.get("slot_start"));
    }

    @Transactional
    public void release(String lockKey) {
        Long tid = TenantContext.requireTenantId();
        Map<String, Object> lock = jdbc.queryForMap(
                "SELECT * FROM sch_lock WHERE tenant_id=? AND lock_key=? FOR UPDATE", tid, lockKey);
        if (!"LOCKED".equals(lock.get("status"))) return;
        jdbc.update("UPDATE sch_lock SET status='RELEASED' WHERE tenant_id=? AND lock_key=?", tid, lockKey);
        jdbc.update("""
                UPDATE sch_slot SET status=IF(used_count-1<=0,'OPEN','LOCKED'), used_count=GREATEST(used_count-1,0)
                WHERE tenant_id=? AND resource_id=? AND slot_date=? AND slot_start=?
                """, tid, lock.get("resource_id"), lock.get("slot_date"), lock.get("slot_start"));
    }

    /** 定时任务：释放超时未付定金的锁档。 */
    @Transactional
    public int releaseExpired() {
        int total = 0;
        List<Long> tenants = jdbc.queryForList("SELECT tenant_id FROM platform_tenant WHERE deleted=0", Long.class);
        for (Long tid : tenants) {
            List<Map<String, Object>> expired = jdbc.queryForList("""
                    SELECT * FROM sch_lock WHERE tenant_id=? AND status='LOCKED' AND expire_at < NOW() LIMIT 500
                    """, tid);
            for (Map<String, Object> lock : expired) {
                jdbc.update("UPDATE sch_lock SET status='RELEASED' WHERE tenant_id=? AND id=?", tid, lock.get("id"));
                jdbc.update("""
                        UPDATE sch_slot SET status=IF(used_count-1<=0,'OPEN','LOCKED'), used_count=GREATEST(used_count-1,0)
                        WHERE tenant_id=? AND resource_id=? AND slot_date=? AND slot_start=?
                        """, tid, lock.get("resource_id"), lock.get("slot_date"), lock.get("slot_start"));
                total++;
            }
        }
        return total;
    }
}
