package com.leon.testtask.controller;

import com.leon.testtask.entity.TimeRecord;
import com.leon.testtask.service.TimeRecordingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TimeRecordController.class)
class TimeRecordControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TimeRecordingService timeRecordingService;


    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testGetRecords_ShouldReturnJsonResponse() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        List<TimeRecord> records = Arrays.asList(
            createTimeRecord(1L, now.minusSeconds(2)),
            createTimeRecord(2L, now.minusSeconds(1))
        );

        when(timeRecordingService.getAllRecords()).thenReturn(records);
        when(timeRecordingService.isDatabaseConnected()).thenReturn(true);
        when(timeRecordingService.getPendingRecordsCount()).thenReturn(0);
        when(timeRecordingService.getTotalRecordCount()).thenReturn(2L);

        mockMvc.perform(get("/api/records"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.records", hasSize(2)))
                .andExpect(jsonPath("$.total", is(2)))
                .andExpect(jsonPath("$.databaseConnected", is(true)))
                .andExpect(jsonPath("$.pendingRecords", is(0)))
                .andExpect(jsonPath("$.records[0].id", is(1)))
                .andExpect(jsonPath("$.records[1].id", is(2)));
    }

    @Test
    void testGetRecords_WithSingleRecord_ShouldReturnJsonResponse() throws Exception {
        List<TimeRecord> records = Arrays.asList(createTimeRecord(1L, LocalDateTime.now()));
        
        when(timeRecordingService.getAllRecords()).thenReturn(records);
        when(timeRecordingService.isDatabaseConnected()).thenReturn(true);
        when(timeRecordingService.getPendingRecordsCount()).thenReturn(0);
        when(timeRecordingService.getTotalRecordCount()).thenReturn(2L);

        mockMvc.perform(get("/api/records"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.records", hasSize(1)))
                .andExpect(jsonPath("$.total", is(1)));
    }

    @Test
    void testGetRecords_DatabaseUnavailable_ShouldReturnServiceUnavailable() throws Exception {
        when(timeRecordingService.getAllRecords())
            .thenThrow(new RuntimeException("Database connection failed"));
        when(timeRecordingService.isDatabaseConnected()).thenReturn(false);
        when(timeRecordingService.getPendingRecordsCount()).thenReturn(5);
        mockMvc.perform(get("/api/records"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error", is("Не удалось получить записи")))
                .andExpect(jsonPath("$.message", is("Database connection failed")))
                .andExpect(jsonPath("$.databaseConnected", is(false)))
                .andExpect(jsonPath("$.pendingRecords", is(5)));
    }

    @Test
    void testGetRecords_EmptyDatabase_ShouldReturnEmptyList() throws Exception {
        when(timeRecordingService.getAllRecords()).thenReturn(Arrays.asList());
        when(timeRecordingService.isDatabaseConnected()).thenReturn(true);
        when(timeRecordingService.getPendingRecordsCount()).thenReturn(0);
        mockMvc.perform(get("/api/records"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.records", hasSize(0)))
                .andExpect(jsonPath("$.total", is(0)))
                .andExpect(jsonPath("$.databaseConnected", is(true)))
                .andExpect(jsonPath("$.pendingRecords", is(0)));
    }

    @Test
    void testGetRecords_DatabaseDisconnectedWithPendingRecords_ShouldReflectStatus() throws Exception {
        List<TimeRecord> records = Arrays.asList(createTimeRecord(1L, LocalDateTime.now()));
        
        when(timeRecordingService.getAllRecords()).thenReturn(records);
        when(timeRecordingService.isDatabaseConnected()).thenReturn(false);
        when(timeRecordingService.getPendingRecordsCount()).thenReturn(10);
        mockMvc.perform(get("/api/records"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.records", hasSize(1)))
                .andExpect(jsonPath("$.total", is(1)))
                .andExpect(jsonPath("$.databaseConnected", is(false)))
                .andExpect(jsonPath("$.pendingRecords", is(10)));
    }

    @Test
    void testGetStatus_ShouldReturnCurrentStatus() throws Exception {
        when(timeRecordingService.isDatabaseConnected()).thenReturn(true);
        when(timeRecordingService.getPendingRecordsCount()).thenReturn(3);
        when(timeRecordingService.getTotalRecordCount()).thenReturn(100L);
        mockMvc.perform(get("/api/status"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.databaseConnected", is(true)))
                .andExpect(jsonPath("$.pendingRecords", is(3)));
    }

    @Test
    void testGetStatus_DatabaseDisconnected_ShouldReturnDisconnectedStatus() throws Exception {
        when(timeRecordingService.isDatabaseConnected()).thenReturn(false);
        when(timeRecordingService.getPendingRecordsCount()).thenReturn(15);
        when(timeRecordingService.getTotalRecordCount()).thenReturn(50L);
        mockMvc.perform(get("/api/status"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.databaseConnected", is(false)))
                .andExpect(jsonPath("$.pendingRecords", is(15)));
    }

    @Test
    void testGetRecords_LargeNumberOfRecords_ShouldHandleCorrectly() throws Exception {
        List<TimeRecord> records = new java.util.ArrayList<>();
        for (int i = 1; i <= 1000; i++) {

            records.add(createTimeRecord((long) i, LocalDateTime.now().minusSeconds(i)));
        }
        
        when(timeRecordingService.getAllRecords()).thenReturn(records);
        when(timeRecordingService.isDatabaseConnected()).thenReturn(true);
        when(timeRecordingService.getPendingRecordsCount()).thenReturn(0);
        when(timeRecordingService.getTotalRecordCount()).thenReturn(2L);
        mockMvc.perform(get("/api/records"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.records", hasSize(1000)))
                .andExpect(jsonPath("$.total", is(1000)));

    }

    private TimeRecord createTimeRecord(Long id, LocalDateTime recordedTime) {
        return TimeRecord.builder()
            .id(id)
            .recordedTime(recordedTime)
            .createdAt(recordedTime.plusNanos(123000000))
            .build();
    }
}