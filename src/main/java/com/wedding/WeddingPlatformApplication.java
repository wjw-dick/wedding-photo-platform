package com.wedding;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@MapperScan("com.wedding.**.mapper")
@SpringBootApplication
public class WeddingPlatformApplication {
    public static void main(String[] args) {
        SpringApplication.run(WeddingPlatformApplication.class, args);
    }
}
