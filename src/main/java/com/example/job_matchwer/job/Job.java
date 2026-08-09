package com.example.job_matchwer.job;

import com.example.job_matchwer.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(
        name = "app_jobs",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_jobs_external_source",
                columnNames = {"source", "external_id"}
        ),
        indexes = {
                @Index(name = "idx_jobs_posted_at", columnList = "posted_at"),
                @Index(name = "idx_jobs_status", columnList = "status")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class Job extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private JobSource source;

    @Column(name = "external_id", nullable = false, length = 255)
    private String externalId;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(name = "company", nullable = false, length = 255)
    private String company;

    @Column(length = 255)
    private String location;

    @Column(name = "remote_type", length = 20)
    @Enumerated(EnumType.STRING)
    private RemoteType remoteType;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String description;

    @Column(name = "apply_url", length = 1000)
    private String applyUrl;

    @Column(name = "posted_at")
    private Instant postedAt;

    @Column(name = "ingested_at", nullable = false)
    private Instant ingestedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload", columnDefinition = "jsonb", nullable = false)
    private String rawPayload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private JobStatus status = JobStatus.INGESTED;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parsed_requirements", columnDefinition = "jsonb")
    private String parsedRequirements;

    @Column(name = "parse_error", length = 1000)
    private String parseError;

    public Job(JobSource source, String externalId, String title, String company,
               String description, String rawPayload) {
        this.source = source;
        this.externalId = externalId;
        this.title = title;
        this.company = company;
        this.description = description;
        this.rawPayload = rawPayload;
        this.ingestedAt = Instant.now();
        this.status = JobStatus.INGESTED;
    }

    public void markParsing() {
        this.status = JobStatus.PARSING;
        this.parseError = null;
    }

    public void markParsed(String parsedRequirements) {
        this.status = JobStatus.PARSED;
        this.parsedRequirements = parsedRequirements;
        this.parseError = null;
    }

    public void markFailed(String error) {
        this.status = JobStatus.FAILED;
        this.parseError = error != null && error.length() > 1000
                ? error.substring(0, 1000)
                : error;
    }
}