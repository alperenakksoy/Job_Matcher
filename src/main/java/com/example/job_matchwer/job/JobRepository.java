package com.example.job_matchwer.job;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface JobRepository extends JpaRepository<Job, UUID> {
    Optional<Job> findBySourceAndExternalId(JobSource source, String externalId);
}
