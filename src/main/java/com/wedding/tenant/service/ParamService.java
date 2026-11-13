package com.wedding.tenant.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wedding.common.api.BizException;
import com.wedding.common.context.TenantContext;
import com.wedding.tenant.entity.SysParam;
import com.wedding.tenant.mapper.SysParamMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 系统参数：读写 Redis 缓存，变更后发布刷新广播，前台 1 分钟内生效。 */
@Service
@RequiredArgsConstructor
public class ParamService {
    private final SysParamMapper paramMapper;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public Map<String, String> all() {
        Long tenantId = TenantContext.requireTenantId();
        String key = "tenant:" + tenantId + ":params";
        String cached = redis.opsForValue().get(key);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, new TypeReference<>() {
                });
            } catch (Exception ignored) {
            }
        }
        List<SysParam> list = paramMapper.selectList(new LambdaQueryWrapper<SysParam>()
                .eq(SysParam::getTenantId, tenantId));
        Map<String, String> map = new LinkedHashMap<>();
        for (SysParam p : list) map.put(p.getParamKey(), p.getParamValue());
        try {
            redis.opsForValue().set(key, objectMapper.writeValueAsString(map), Duration.ofMinutes(10));
        } catch (Exception ignored) {
        }
        return map;
    }

    public String get(String key, String defaultValue) {
        return all().getOrDefault(key, defaultValue);
    }

    @Transactional
    public void save(Map<String, String> params) {
        Long tenantId = TenantContext.requireTenantId();
        for (Map.Entry<String, String> e : params.entrySet()) {
            SysParam p = paramMapper.selectOne(new LambdaQueryWrapper<SysParam>()
                    .eq(SysParam::getParamKey, e.getKey()));
            if (p == null) {
                p = new SysParam();
                p.setParamGroup("custom");
                p.setParamKey(e.getKey());
                p.setParamValue(e.getValue());
                p.setValueType("STRING");
                paramMapper.insert(p);
            } else {
                p.setParamValue(e.getValue());
                paramMapper.updateById(p);
            }
        }
        redis.delete("tenant:" + tenantId + ":params");
        redis.convertAndSend("tenant:param:refresh", String.valueOf(tenantId));
    }

    public Map<String, List<Map<String, Object>>> grouped() {
        List<Map<String, Object>> rows = paramMapper.selectMaps(new LambdaQueryWrapper<SysParam>()
                .select(SysParam::getParamGroup, SysParam::getParamKey, SysParam::getParamValue,
                        SysParam::getValueType, SysParam::getRemark)
                .orderByAsc(SysParam::getParamGroup));
        Map<String, List<Map<String, Object>>> result = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String group = String.valueOf(row.getOrDefault("param_group", "custom"));
            result.computeIfAbsent(group, k -> new java.util.ArrayList<>()).add(row);
        }
        return result;
    }

    public int getInt(String key, int defaultValue) {
        try {
            return Integer.parseInt(get(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            throw new BizException("参数 " + key + " 不是有效数字");
        }
    }
}
