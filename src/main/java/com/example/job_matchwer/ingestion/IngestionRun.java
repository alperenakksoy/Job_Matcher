package com.example.job_matchwer.ingestion;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Setter
@Table(name = "ingestion_runs")
public class IngestionRun {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private JobSource source;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    private Instant startedAt;

    @Column(nullable = false, updatable = false)
    private int jobsFetched;

    @Column(nullable = false, updatable = false)
    private int jobsCreated;

    @Column(nullable = false, updatable = false)
    private int jobsSkipped;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private IngestionStatus status;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

}
