package com.example.job_matchwer.ingestion;

import com.example.job_matchwer.job.Job;
import com.example.job_matchwer.job.JobRepository;
import com.example.job_matchwer.job.JobSource;
import com.example.job_matchwer.job.RemoteType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class IngestionService {

    private final ArbeitnowClient arbeitnowClient;
    private final JobRepository jobRepository;
    private final IngestionRunRepository ingestionRunRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    public IngestionRun runIngestion() {
        IngestionRun run = new IngestionRun();
        run.setSource(JobSource.ARBEITNOW);
        run.setCreatedAt(Instant.now());
        run.setStartedAt(Instant.now());
        run.setStatus(IngestionStatus.RUNNING);
        run = ingestionRunRepository.save(run);

        List<ArbeitnowJobDto> jobs;
        try {
            jobs = arbeitnowClient.getJobs().data();
        } catch (Exception e) {
            log.error("Ingestion run {} failed while fetching jobs", run.getId(), e);
            run.setStatus(IngestionStatus.FAILED);
            run.setCompletedAt(Instant.now());
            run.setErrorMessage(truncate(e.getMessage()));
            return ingestionRunRepository.save(run);
        }

        int created = 0;
        int skipped = 0;

        for (ArbeitnowJobDto dto : jobs) {
            try {
                boolean exists = jobRepository
                        .findBySourceAndExternalId(JobSource.ARBEITNOW, dto.slug())
                        .isPresent();

                if (exists) {
                    skipped++;
                    continue;
                }

                Job job = mapToJob(dto);
                job = jobRepository.save(job);
                created++;

                eventPublisher.publishEvent(new JobIngestedEvent(
                        job.getId(), JobSource.ARBEITNOW, job.getExternalId(), Instant.now()
                ));

            } catch (DataIntegrityViolationException e) {
                // race condition: another process inserted the same (source, externalId) first
                log.warn("Duplicate job during concurrent ingestion, skipping slug={}", dto.slug());
                skipped++;
            } catch (RuntimeException e) {
                log.error("Failed to process job slug={}", dto.slug(), e);
                skipped++;
            }
        }

        run.setStatus(IngestionStatus.COMPLETED);
        run.setCompletedAt(Instant.now());
        run.setJobsFetched(jobs.size());
        run.setJobsCreated(created);
        run.setJobsSkipped(skipped);

        return ingestionRunRepository.save(run);
    }

    private Job mapToJob(ArbeitnowJobDto dto) {
        Job job = new Job(
                JobSource.ARBEITNOW,
                dto.slug(),
                dto.title(),
                dto.companyName(),
                stripHtml(dto.description()),
                objectMapper.writeValueAsString(dto)
        );

        job.setLocation(dto.location());
        job.setRemoteType(dto.remote() ? RemoteType.REMOTE : RemoteType.ONSITE);
        job.setApplyUrl(dto.url());
        job.setPostedAt(Instant.ofEpochSecond(dto.createdAt()));

        return job;
    }

    private String stripHtml(String html) {
        if (html == null) return null;
        return html.replaceAll("<[^>]+>", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String truncate(String message) {
        if (message == null) return null;
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }
}