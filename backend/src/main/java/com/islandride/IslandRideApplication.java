package com.islandride;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.TimeZone;

@SpringBootApplication
@EnableAsync
public class IslandRideApplication {

    public static void main(String[] args) {
        // Enforce canonical UTC timezone across application and database drivers
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(IslandRideApplication.class, args);
    }
}

