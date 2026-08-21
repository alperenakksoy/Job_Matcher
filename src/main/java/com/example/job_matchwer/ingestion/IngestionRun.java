package com.example.job_matchwer.ingestion;

import com.example.job_matchwer.job.JobSource;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Setter
@Table(name = "ingestion_runs")
public class IngestionRun {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private JobSource source;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false, updatable = false)
    private Instant startedAt;

    private Instant completedAt;

    @Column(nullable = false)
    private int jobsFetched;

    @Column(nullable = false)
    private int jobsCreated;

    @Column(nullable = false)
    private int jobsSkipped;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IngestionStatus status;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;
}