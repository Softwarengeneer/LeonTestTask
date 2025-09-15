package com.leon.testtask.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalStateException(
            IllegalStateException ex, WebRequest request) {
        
        log.warn("Состояние системы не позволяет выполнить операцию: {}", ex.getMessage());
        
        Map<String, Object> errorResponse = Map.of(
            "error", "Ошибка состояния системы",
            "message", ex.getMessage(),
            "timestamp", LocalDateTime.now(),
            "path", request.getDescription(false).replace("uri=", "")
        );
        
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(errorResponse);
    }
    
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolationException(
            ConstraintViolationException ex, WebRequest request) {
        
        log.warn("Ошибка валидации параметров: {}", ex.getMessage());
        
        String violations = ex.getConstraintViolations()
            .stream()
            .map(ConstraintViolation::getMessage)
            .collect(Collectors.joining(", "));
        
        Map<String, Object> errorResponse = Map.of(
            "error", "Ошибка валидации",
            "message", violations,
            "timestamp", LocalDateTime.now(),
            "path", request.getDescription(false).replace("uri=", "")
        );
        
        return ResponseEntity.badRequest().body(errorResponse);
    }
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException ex, WebRequest request) {
        
        log.warn("Ошибка валидации аргументов метода: {}", ex.getMessage());
        
        String fieldErrors = ex.getBindingResult()
            .getFieldErrors()
            .stream()
            .map(FieldError::getDefaultMessage)
            .collect(Collectors.joining(", "));
        
        Map<String, Object> errorResponse = Map.of(
            "error", "Ошибка валидации параметров",
            "message", fieldErrors,
            "timestamp", LocalDateTime.now(),
            "path", request.getDescription(false).replace("uri=", "")
        );
        
        return ResponseEntity.badRequest().body(errorResponse);
    }
    
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleMethodArgumentTypeMismatchException(
            MethodArgumentTypeMismatchException ex, WebRequest request) {
        
        log.warn("Ошибка типа параметра: {} = {}", ex.getName(), ex.getValue());
        
        Map<String, Object> errorResponse = Map.of(
            "error", "Неверный тип параметра",
            "message", String.format("Параметр '%s' имеет неверный тип", ex.getName()),
            "timestamp", LocalDateTime.now(),
            "path", request.getDescription(false).replace("uri=", "")
        );
        
        return ResponseEntity.badRequest().body(errorResponse);
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneralException(
            Exception ex, WebRequest request) {
        
        log.error("Неожиданная ошибка: {}", ex.getMessage(), ex);
        
        Map<String, Object> errorResponse = Map.of(
            "error", "Внутренняя ошибка сервера",
            "message", "Произошла неожиданная ошибка",
            "timestamp", LocalDateTime.now(),
            "path", request.getDescription(false).replace("uri=", "")
        );
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }
}