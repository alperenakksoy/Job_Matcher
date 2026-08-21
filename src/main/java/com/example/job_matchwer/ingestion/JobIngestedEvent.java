package com.example.job_matchwer.ingestion;

import com.example.job_matchwer.job.JobSource;

import java.time.Instant;
import java.util.UUID;

public record JobIngestedEvent(UUID jobId, JobSource source, String externalId, Instant ingestedAt) {}
