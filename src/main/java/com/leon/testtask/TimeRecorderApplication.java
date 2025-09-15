package com.leon.testtask;

import com.leon.testtask.service.TimeRecordingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import jakarta.annotation.PreDestroy;

@SpringBootApplication
public class TimeRecorderApplication {

    @Autowired
    private TimeRecordingService timeRecordingService;

    public static void main(String[] args) {
        SpringApplication.run(TimeRecorderApplication.class, args);
    }

    @PreDestroy
    public void onDestroy() {
        if (timeRecordingService != null) {
            timeRecordingService.shutdown();
        }
    }
}