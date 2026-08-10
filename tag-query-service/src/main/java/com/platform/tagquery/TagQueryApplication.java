package com.platform.tagquery;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan({"com.platform.tagquery.repository.mysql"})
@EnableAsync
@EnableScheduling
public class TagQueryApplication {
    public static void main(String[] args) {
        SpringApplication.run(TagQueryApplication.class, args);
    }
}
