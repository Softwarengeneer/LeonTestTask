package com.leon.testtask.integration;

import com.leon.testtask.TimeRecorderApplication;
import com.leon.testtask.entity.TimeRecord;
import com.leon.testtask.repository.TimeRecordRepository;
import com.leon.testtask.service.TimeRecordingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(
    classes = TimeRecorderApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@Testcontainers
@ActiveProfiles("integration-test")
@AutoConfigureWebMvc
class TimeRecorderIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("timerecorder_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private TimeRecordRepository timeRecordRepository;

    @Autowired
    private TimeRecordingService timeRecordingService;

    @Autowired
    private ObjectMapper objectMapper;

    @LocalServerPort
    private int port;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        timeRecordRepository.deleteAll();
    }

    @Test
    void testFullApplicationFlow_RecordingAndRetrieval() throws Exception {
        await().atMost(5, TimeUnit.SECONDS)
               .untilAsserted(() -> assertTrue(timeRecordRepository.count() > 0));
        mockMvc.perform(get("/api/records"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.records", not(empty())))
                .andExpect(jsonPath("$.total", greaterThan(0)))
                .andExpect(jsonPath("$.databaseConnected", is(true)))
                .andExpect(jsonPath("$.pendingRecords", is(0)));

        mockMvc.perform(get("/api/status"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.databaseConnected", is(true)))
                .andExpect(jsonPath("$.pendingRecords", is(0)));
    }

    @Test
    void testDatabasePersistence_RecordsAreStoredInChronologicalOrder() throws Exception {
        await().atMost(10, TimeUnit.SECONDS)
               .untilAsserted(() -> assertTrue(timeRecordRepository.count() >= 5));

        List<TimeRecord> records = timeRecordRepository.findAllOrderedByRecordedTime();
        assertTrue(records.size() >= 5);
        for (int i = 1; i < records.size(); i++) {
            LocalDateTime current = records.get(i).getRecordedTime();
            LocalDateTime previous = records.get(i - 1).getRecordedTime();
            assertTrue(current.isAfter(previous) || current.isEqual(previous),
                "Записи должны быть в хронологическом порядке: " + previous + " -> " + current);
        }

        for (int i = 1; i < records.size(); i++) {
            Long currentId = records.get(i).getId();
            Long previousId = records.get(i - 1).getId();
            assertTrue(currentId > previousId,
                "ID должны быть последовательными: " + previousId + " -> " + currentId);
        }
    }

    @Test
    void testTimeRecordingContinuity_RecordsCreatedEverySecond() throws Exception {
        long initialCount = timeRecordRepository.count();

        Thread.sleep(3500);

        long finalCount = timeRecordRepository.count();
        assertTrue(finalCount >= initialCount + 3,
            "Должно быть минимум 3 новые записи за 3+ секунды");

        List<TimeRecord> recentRecords = timeRecordRepository.findAllOrderedByRecordedTime();
        if (recentRecords.size() >= 3) {
            for (int i = 1; i < Math.min(4, recentRecords.size()); i++) {
                LocalDateTime current = recentRecords.get(i).getRecordedTime();
                LocalDateTime previous = recentRecords.get(i - 1).getRecordedTime();
                
                long diffSeconds = java.time.Duration.between(previous, current).getSeconds();
                assertTrue(diffSeconds >= 0 && diffSeconds <= 2,
                    "Разница во времени должна быть около 1 секунды, но была: " + diffSeconds);
            }
        }
    }

    @Test
    void testServiceStatus_ReflectsActualDatabaseState() {
        assertTrue(timeRecordingService.isDatabaseConnected());
        assertEquals(0, timeRecordingService.getPendingRecordsCount());

        assertDoesNotThrow(() -> {
            List<TimeRecord> records = timeRecordingService.getAllRecords();
            assertNotNull(records);
        });
    }

    @Test
    void testEntityConstraints_ValidTimeRecords() throws Exception {
        await().atMost(3, TimeUnit.SECONDS)
               .untilAsserted(() -> assertTrue(timeRecordRepository.count() > 0));

        List<TimeRecord> records = timeRecordRepository.findAll();

        for (TimeRecord record : records) {
            assertNotNull(record.getId(), "ID не должен быть null");
            assertNotNull(record.getRecordedTime(), "Время записи не должно быть null");
            assertNotNull(record.getCreatedAt(), "Время создания не должно быть null");

            LocalDateTime now = LocalDateTime.now();
            assertTrue(record.getRecordedTime().isBefore(now.plusSeconds(1)),
                "Время записи не должно быть в будущем");
            assertTrue(record.getRecordedTime().isAfter(now.minusMinutes(5)),
                "Время записи не должно быть слишком далеко в прошлом");

            long diffMillis = Math.abs(
                java.time.Duration.between(record.getRecordedTime(), record.getCreatedAt()).toMillis()
            );
            assertTrue(diffMillis < 1000,
                "CreatedAt должно быть в пределах 1 секунды от RecordedTime, разница была: " + diffMillis + "мс");
        }
    }

    @Test
    void testConcurrentRequests_NoDataCorruption() throws Exception {
        await().atMost(3, TimeUnit.SECONDS)
               .untilAsserted(() -> assertTrue(timeRecordRepository.count() > 0));

        Thread[] threads = new Thread[10];
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(() -> {
                try {
                    for (int j = 0; j < 5; j++) {
                        mockMvc.perform(get("/api/records"))
                                .andExpect(status().isOk());
                        mockMvc.perform(get("/api/status"))
                                .andExpect(status().isOk());
                        Thread.sleep(100);
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            threads[i].start();
        }

        for (Thread thread : threads) {
            thread.join(5000);
        }

        List<TimeRecord> records = timeRecordRepository.findAllOrderedByRecordedTime();
        assertFalse(records.isEmpty());
        
        long uniqueIds = records.stream().mapToLong(TimeRecord::getId).distinct().count();
        assertEquals(records.size(), uniqueIds, "Все записи должны иметь уникальные ID");
    }
}