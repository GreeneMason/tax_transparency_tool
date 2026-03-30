package com.govlens;

/** Application entry point for the GovLens Spring Boot service. */

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class GovLensApplication {

    public static void main(String[] args) {
        SpringApplication.run(GovLensApplication.class, args);
    }
}
