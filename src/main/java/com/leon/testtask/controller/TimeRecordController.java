package com.leon.testtask.controller;

import com.leon.testtask.entity.TimeRecord;
import com.leon.testtask.service.TimeRecordingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
@Validated
public class TimeRecordController {

    private final TimeRecordingService timeRecordingService;
    
    @GetMapping("/records")
    public ResponseEntity<Map<String, Object>> getRecords() {

        log.debug("Запрос записей");

        try {
            List<TimeRecord> records = timeRecordingService.getAllRecords();
            return generateJsonResponse(records);

        } catch (IllegalStateException e) {
            log.warn("БД недоступна при запросе записей: {}", e.getMessage());
            return generateErrorResponse(e);
        } catch (Exception e) {
            log.error("Неожиданная ошибка при получении записей", e);
            return generateErrorResponse(e);
        }
    }
    
    private ResponseEntity<Map<String, Object>> generateJsonResponse(List<TimeRecord> records) {
        Map<String, Object> response = Map.of(
            "records", records,
            "total", records.size(),
            "databaseConnected", timeRecordingService.isDatabaseConnected(),
            "pendingRecords", timeRecordingService.getPendingRecordsCount(),
            "totalRecordCount", timeRecordingService.getTotalRecordCount()
        );
        
        return ResponseEntity.ok(response);
    }
    
    private ResponseEntity<Map<String, Object>> generateErrorResponse(Exception e) {
        Map<String, Object> errorResponse = Map.of(
            "error", "Не удалось получить записи",
            "message", e.getMessage(),
            "databaseConnected", timeRecordingService.isDatabaseConnected(),
            "pendingRecords", timeRecordingService.getPendingRecordsCount(),
            "timestamp", System.currentTimeMillis()
        );
        
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(errorResponse);
    }
    
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        log.debug("Запрос статуса системы");
        
        Map<String, Object> status = Map.of(
            "databaseConnected", timeRecordingService.isDatabaseConnected(),
            "pendingRecords", timeRecordingService.getPendingRecordsCount(),
            "totalRecordCount", timeRecordingService.getTotalRecordCount(),
            "timestamp", System.currentTimeMillis()
        );
        
        return ResponseEntity.ok(status);
    }
}