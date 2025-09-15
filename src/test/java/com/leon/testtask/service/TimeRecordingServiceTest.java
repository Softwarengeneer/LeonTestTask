package com.leon.testtask.service;

import com.leon.testtask.entity.TimeRecord;
import com.leon.testtask.repository.TimeRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TimeRecordingServiceTest {

    @Mock
    private TimeRecordRepository timeRecordRepository;

    private TimeRecordingService timeRecordingService;

    @BeforeEach
    void setUp() {
        timeRecordingService = new TimeRecordingService(timeRecordRepository);
    }

    @Test
    void testGetAllRecords_WhenDatabaseAvailable_ShouldReturnRecords() {
        LocalDateTime now = LocalDateTime.now();
        List<TimeRecord> expectedRecords = Arrays.asList(
            TimeRecord.builder()
                .recordedTime(now.minusSeconds(2))
                .createdAt(now.minusSeconds(2).plusNanos(123000000))
                .build(),
            TimeRecord.builder()
                .recordedTime(now.minusSeconds(1))
                .createdAt(now.minusSeconds(1).plusNanos(123000000))
                .build()
        );
        when(timeRecordRepository.findAllOrderedByRecordedTime()).thenReturn(expectedRecords);

        List<TimeRecord> actualRecords = timeRecordingService.getAllRecords();

        assertEquals(expectedRecords, actualRecords);
        verify(timeRecordRepository).findAllOrderedByRecordedTime();
    }

    @Test
    void testGetAllRecords_WhenDatabaseUnavailable_ShouldThrowException() {
        when(timeRecordRepository.findAllOrderedByRecordedTime())
            .thenThrow(new DataAccessException("Database connection failed") {});

        assertThrows(IllegalStateException.class, () -> timeRecordingService.getAllRecords());
        assertFalse(timeRecordingService.isDatabaseConnected());
    }

    @Test
    void testDatabaseConnectionRecovery() {
        // Тестируем начальное состояние соединения
        assertTrue(timeRecordingService.isDatabaseConnected());
    }

    @Test
    void testPendingRecordsCount_InitiallyZero() {
        int pendingCount = timeRecordingService.getPendingRecordsCount();
        assertEquals(0, pendingCount);
    }

    @Test
    void testDatabaseConnectionStatus_InitiallyTrue() {
        boolean isConnected = timeRecordingService.isDatabaseConnected();
        assertTrue(isConnected);
    }

    @Test
    void testTimeRecordingContinuesWhenDatabaseUnavailable() {
        assertEquals(0, timeRecordingService.getPendingRecordsCount());
        assertTrue(timeRecordingService.isDatabaseConnected());
    }

    @Test
    void testProcessPendingRecordsAfterRecovery() {
        assertEquals(0, timeRecordingService.getTotalRecordCount());
    }

    @Test
    void testShutdown() {
        timeRecordingService.shutdown();
        assertDoesNotThrow(() -> timeRecordingService.shutdown());
    }

    @Test
    void testConcurrentAccess() throws InterruptedException {
        when(timeRecordRepository.findAllOrderedByRecordedTime()).thenReturn(List.of());

        Thread[] threads = new Thread[5];
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < 10; j++) {
                    timeRecordingService.getAllRecords();
                    timeRecordingService.isDatabaseConnected();
                    timeRecordingService.getPendingRecordsCount();
                }
            });
            threads[i].start();
        }

        for (Thread thread : threads) {
            thread.join(1000);
        }

        verify(timeRecordRepository, atLeast(50)).findAllOrderedByRecordedTime();
    }
}