package com.wedding.common.util;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class BizNo {
    private static final AtomicLong SEQ = new AtomicLong(0);

    public String next(String prefix) {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        return prefix + ts + String.format("%04d", SEQ.incrementAndGet() % 10000);
    }

    public String orderNo() {
        return next("WO");
    }

    public String dateKey() {
        return LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
    }
}
