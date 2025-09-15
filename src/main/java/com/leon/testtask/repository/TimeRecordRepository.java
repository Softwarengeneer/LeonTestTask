package com.leon.testtask.repository;

import com.leon.testtask.entity.TimeRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TimeRecordRepository extends JpaRepository<TimeRecord, Long> {
    
    @Query("SELECT tr FROM TimeRecord tr ORDER BY tr.recordedTime ASC")
    List<TimeRecord> findAllOrderedByRecordedTime();
}