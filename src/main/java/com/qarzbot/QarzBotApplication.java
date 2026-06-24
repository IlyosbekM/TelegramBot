package com.qarzbot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class QarzBotApplication {
    public static void main(String[] args) {
        SpringApplication.run(QarzBotApplication.class, args);
    }
}
