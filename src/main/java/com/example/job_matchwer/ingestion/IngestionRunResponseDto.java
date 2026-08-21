package com.example.job_matchwer.ingestion;

import com.example.job_matchwer.job.JobSource;

import java.time.Instant;
import java.util.UUID;

public record IngestionRunResponseDto(
        UUID id,
        JobSource source,
        Instant startedAt,
        Instant completedAt,
        int jobsFetched,
        int jobsCreated,
        int jobsSkipped,
        IngestionStatus status,
        String errorMessage
) {}