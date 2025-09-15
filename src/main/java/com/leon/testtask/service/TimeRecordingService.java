package com.leon.testtask.service;

import com.leon.testtask.entity.TimeRecord;
import com.leon.testtask.repository.TimeRecordRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Service
@Slf4j
public class TimeRecordingService {
    
    private static final String THREAD_NAME_FORMAT = "time-recorder-%d";
    
    private final TimeRecordRepository timeRecordRepository;
    private final ScheduledExecutorService scheduler;
    private final ConcurrentLinkedQueue<TimeRecord> pendingRecords = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean isDatabaseAvailable = new AtomicBoolean(true);
    private final AtomicLong totalRecordCount = new AtomicLong(0);
    
    @Value("${app.time-recording.interval-seconds:1}")
    private int recordingIntervalSeconds;
    
    @Value("${app.time-recording.recovery-interval-seconds:5}")
    private int recoveryIntervalSeconds;
    
    @Value("${app.time-recording.shutdown-timeout-seconds:10}")
    private int shutdownTimeoutSeconds;
    
    public TimeRecordingService(TimeRecordRepository timeRecordRepository) {
        this.timeRecordRepository = timeRecordRepository;
        this.scheduler = Executors.newScheduledThreadPool(2, this::createThread);
    }
    
    private Thread createThread(Runnable runnable) {
        Thread thread = new Thread(runnable, 
            String.format(THREAD_NAME_FORMAT, Thread.currentThread().getId()));
        thread.setDaemon(true);
        return thread;
    }
    
    @PostConstruct
    public void startTimeRecording() {
        log.info("Инициализация сервиса записи времени с интервалом {} секунд", recordingIntervalSeconds);
        
        scheduler.scheduleAtFixedRate(this::recordTimePoint, 
            0, recordingIntervalSeconds, TimeUnit.SECONDS);
        
        scheduler.scheduleAtFixedRate(this::attemptDatabaseRecovery, 
            recoveryIntervalSeconds, recoveryIntervalSeconds, TimeUnit.SECONDS);
    }
    
    private void recordTimePoint() {
        try {
            LocalDateTime currentTime = LocalDateTime.now();
            TimeRecord record = TimeRecord.builder()
                .recordedTime(currentTime)
                .build();
            
            if (isDatabaseAvailable.get()) {
                try {
                    saveRecord(record);
                    totalRecordCount.incrementAndGet();
                    log.debug("Запись времени успешно сохранена: {}", currentTime);
                    
                    processPendingRecords();
                } catch (DataAccessException e) {
                    log.error("Соединение с БД потеряно. Добавляем запись в очередь: {}", currentTime);
                    isDatabaseAvailable.set(false);
                    pendingRecords.offer(record);
                }
            } else {
                log.warn("БД недоступна. Добавляем запись в очередь: {}", currentTime);
                pendingRecords.offer(record);
            }
        } catch (Exception e) {
            log.error("Критическая ошибка при записи времени", e);
        }
    }
    
    private void attemptDatabaseRecovery() {
        if (!isDatabaseAvailable.get()) {
            try {
                timeRecordRepository.count();
                log.info("Соединение с БД восстановлено! Обрабатываем {} отложенных записей", 
                    pendingRecords.size());
                isDatabaseAvailable.set(true);
                
                processPendingRecords();
            } catch (DataAccessException e) {
                log.debug("БД все еще недоступна. Повторная попытка через {} секунд...", 
                    recoveryIntervalSeconds);
            }
        }
    }
    
    @Transactional
    protected void saveRecord(TimeRecord record) {
        timeRecordRepository.save(record);
    }
    
    private void processPendingRecords() {
        int processedCount = 0;
        TimeRecord record;
        
        while ((record = pendingRecords.poll()) != null && isDatabaseAvailable.get()) {
            try {
                saveRecord(record);
                totalRecordCount.incrementAndGet();
                processedCount++;
            } catch (DataAccessException e) {
                log.error("Ошибка сохранения отложенной записи, возвращаем в очередь: {}", 
                    record.getRecordedTime());
                pendingRecords.offer(record);
                isDatabaseAvailable.set(false);
                break;
            }
        }
        
        if (processedCount > 0) {
            log.info("Успешно обработано {} отложенных записей из {}", 
                processedCount, processedCount + pendingRecords.size());
        }
    }
    
    @Transactional(readOnly = true)
    public List<TimeRecord> getAllRecords() {
        if (!isDatabaseAvailable.get()) {
            throw new IllegalStateException("БД в настоящее время недоступна");
        }
        
        try {
            return timeRecordRepository.findAllOrderedByRecordedTime();
        } catch (DataAccessException e) {
            log.error("Ошибка получения записей из БД", e);
            isDatabaseAvailable.set(false);
            throw new IllegalStateException("Ошибка доступа к БД", e);
        }
    }
    
    public int getPendingRecordsCount() {
        return pendingRecords.size();
    }
    
    public boolean isDatabaseConnected() {
        return isDatabaseAvailable.get();
    }
    
    public long getTotalRecordCount() {
        return totalRecordCount.get();
    }
    
    @PreDestroy
    public void shutdown() {
        log.info("Завершение работы сервиса записи времени...");
        
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(shutdownTimeoutSeconds, TimeUnit.SECONDS)) {
                log.warn("Принудительное завершение потоков через {} секунд", shutdownTimeoutSeconds);
                scheduler.shutdownNow();
                
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    log.error("Не удалось корректно завершить все потоки");
                }
            }
            
            log.info("Сервис записи времени завершен. Всего записей: {}, в очереди: {}", 
                totalRecordCount.get(), pendingRecords.size());
                
        } catch (InterruptedException e) {
            log.error("Прерывание при завершении сервиса", e);
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}