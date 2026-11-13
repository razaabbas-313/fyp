package com.fotofetch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class FotoFetchApplication {
    public static void main(String[] args) {
        SpringApplication.run(FotoFetchApplication.class, args);
    }
}
