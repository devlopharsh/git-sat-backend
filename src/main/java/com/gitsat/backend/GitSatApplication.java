package com.gitsat.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class GitSatApplication {
    public static void main(String[] args) {
        SpringApplication.run(GitSatApplication.class, args);
    }
}
