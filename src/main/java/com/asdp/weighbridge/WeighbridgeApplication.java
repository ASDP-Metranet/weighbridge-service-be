package com.asdp.weighbridge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class WeighbridgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(WeighbridgeApplication.class, args);
    }
}
