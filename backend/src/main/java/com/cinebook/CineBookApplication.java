package com.cinebook;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CineBookApplication {
    public static void main(String[] args) {
        SpringApplication.run(CineBookApplication.class, args);
        System.out.println("server started at port 8181");
    }
}