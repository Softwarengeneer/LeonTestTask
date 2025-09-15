package com.leon.testtask.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "time_records", indexes = {
    @Index(name = "idx_recorded_time", columnList = "recorded_time")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Builder
public class TimeRecord {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;
    
    @Column(name = "recorded_time", nullable = false)
    @NonNull
    private LocalDateTime recordedTime;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;
    
    public TimeRecord(LocalDateTime recordedTime) {
        this.recordedTime = recordedTime;
    }
}