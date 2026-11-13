package com.wedding.common.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
@RequiredArgsConstructor
public class AuditAspect {
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    @Around("@annotation(audited)")
    public Object around(ProceedingJoinPoint pjp, Audited audited) throws Throwable {
        long start = System.currentTimeMillis();
        String args = safeArgs(pjp.getArgs());
        try {
            Object result = pjp.proceed();
            auditService.record(audited.module(), audited.action(), audited.targetType(),
                    targetId(pjp.getArgs()), args, "SUCCESS", System.currentTimeMillis() - start);
            return result;
        } catch (Throwable e) {
            auditService.record(audited.module(), audited.action(), audited.targetType(),
                    targetId(pjp.getArgs()), args + " | error=" + e.getMessage(), "FAIL",
                    System.currentTimeMillis() - start);
            throw e;
        }
    }

    private String targetId(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof Long l) return String.valueOf(l);
            if (arg instanceof String s && s.length() < 64) return s;
        }
        return null;
    }

    private String safeArgs(Object[] args) {
        try {
            String json = objectMapper.writeValueAsString(args);
            return json.length() > 500 ? json.substring(0, 500) : json;
        } catch (Exception e) {
            return "";
        }
    }
}
