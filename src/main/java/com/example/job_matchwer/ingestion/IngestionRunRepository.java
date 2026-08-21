package com.example.job_matchwer.ingestion;

import lombok.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface IngestionRunRepository extends JpaRepository<@NonNull IngestionRun,@NonNull UUID> {
}