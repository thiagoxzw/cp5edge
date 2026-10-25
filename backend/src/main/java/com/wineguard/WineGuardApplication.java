package com.wineguard;

import com.wineguard.config.WineGuardProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(WineGuardProperties.class)
public class WineGuardApplication {

    public static void main(String[] args) {
        SpringApplication.run(WineGuardApplication.class, args);
    }
}
