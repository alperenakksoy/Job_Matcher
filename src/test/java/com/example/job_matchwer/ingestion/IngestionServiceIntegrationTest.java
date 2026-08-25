package com.example.job_matchwer.ingestion;

import com.example.job_matchwer.job.Job;
import com.example.job_matchwer.job.JobRepository;
import com.example.job_matchwer.job.JobSource;
import com.example.job_matchwer.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.event.EventListener;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.test.context.TestConfiguration;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Covers the behaviour that actually matters for ingestion: a job we've
 * already seen (by source + external id) is skipped rather than duplicated,
 * a new job is saved and published as a JobIngestedEvent exactly once, and
 * the IngestionRun record ends up with counts that match what happened.
 *
 * Runs against a real Postgres (via AbstractIntegrationTest) because the
 * dedup path relies on the real unique constraint on (source, external_id) -
 * an in-memory fake repository would not exercise that constraint.
 * ArbeitnowClient is mocked so the test doesn't depend on the real,
 * changing Arbeitnow API.
 */
class IngestionServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private IngestionService ingestionService;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private IngestionRunRepository ingestionRunRepository;

    @MockitoBean
    private ArbeitnowClient arbeitnowClient;

    @Autowired
    private RecordingEventListener recordingEventListener;

    @AfterEach
    void cleanUp() {
        jobRepository.deleteAll();
        ingestionRunRepository.deleteAll();
        recordingEventListener.received.clear();
    }

    @Test
    void firstRun_createsNewJobsAndPublishesEvents() {
        ArbeitnowJobDto dto = jobDto("first-job-slug");
        when(arbeitnowClient.getAllJobs()).thenReturn(complete(dto));

        IngestionRun run = ingestionService.runIngestion();

        assertThat(run.getStatus()).isEqualTo(IngestionStatus.COMPLETED);
        assertThat(run.getJobsFetched()).isEqualTo(1);
        assertThat(run.getJobsCreated()).isEqualTo(1);
        assertThat(run.getJobsSkipped()).isEqualTo(0);

        Optional<Job> saved = jobRepository.findBySourceAndExternalId(JobSource.ARBEITNOW, "first-job-slug");
        assertThat(saved).isPresent();
        assertThat(saved.get().getTitle()).isEqualTo(dto.title());

        assertThat(recordingEventListener.received).hasSize(1);
        assertThat(recordingEventListener.received.get(0).externalId()).isEqualTo("first-job-slug");
    }

    @Test
    void secondRun_withSameSlug_isSkippedNotDuplicated() {
        ArbeitnowJobDto dto = jobDto("repeat-slug");
        when(arbeitnowClient.getAllJobs()).thenReturn(complete(dto));

        ingestionService.runIngestion();
        recordingEventListener.received.clear();

        IngestionRun secondRun = ingestionService.runIngestion();

        assertThat(secondRun.getStatus()).isEqualTo(IngestionStatus.COMPLETED);
        assertThat(secondRun.getJobsFetched()).isEqualTo(1);
        assertThat(secondRun.getJobsCreated()).isEqualTo(0);
        assertThat(secondRun.getJobsSkipped()).isEqualTo(1);

        long countInDb = jobRepository.findAll().stream()
                .filter(j -> j.getExternalId().equals("repeat-slug"))
                .count();
        assertThat(countInDb).isEqualTo(1);

        // no new event for the skipped/duplicate job
        assertThat(recordingEventListener.received).isEmpty();
    }

    @Test
    void mixedBatch_createsNewOnesAndSkipsExisting() {
        when(arbeitnowClient.getAllJobs()).thenReturn(complete(jobDto("mix-existing")));
        ingestionService.runIngestion();
        recordingEventListener.received.clear();

        when(arbeitnowClient.getAllJobs()).thenReturn(complete(jobDto("mix-existing"), jobDto("mix-new")));
        IngestionRun run = ingestionService.runIngestion();

        assertThat(run.getJobsFetched()).isEqualTo(2);
        assertThat(run.getJobsCreated()).isEqualTo(1);
        assertThat(run.getJobsSkipped()).isEqualTo(1);
        assertThat(recordingEventListener.received).hasSize(1);
        assertThat(recordingEventListener.received.get(0).externalId()).isEqualTo("mix-new");
    }

    @Test
    void fetchFailure_marksRunFailedAndDoesNotThrow() {
        when(arbeitnowClient.getAllJobs()).thenThrow(new IngestionFetchException("boom", new RuntimeException()));

        IngestionRun run = ingestionService.runIngestion();

        assertThat(run.getStatus()).isEqualTo(IngestionStatus.FAILED);
        assertThat(run.getErrorMessage()).isNotBlank();
        assertThat(recordingEventListener.received).isEmpty();
    }

    @Test
    void partialPagination_stillProcessesJobsAlreadyCollected() {
        // Simulates a later page failing (e.g. rate limited) after some
        // pages were already fetched successfully - those jobs should not
        // be discarded, and the run should be marked PARTIAL rather than
        // FAILED so it's clear something was collected but not everything.
        ArbeitnowJobDto dto = jobDto("partial-slug");
        when(arbeitnowClient.getAllJobs()).thenReturn(
                new ArbeitnowClient.PartialIngestionResult(
                        List.of(dto), false, new IngestionFetchException("rate limited", new RuntimeException())
                )
        );

        IngestionRun run = ingestionService.runIngestion();

        assertThat(run.getStatus()).isEqualTo(IngestionStatus.PARTIAL);
        assertThat(run.getJobsFetched()).isEqualTo(1);
        assertThat(run.getJobsCreated()).isEqualTo(1);
        assertThat(run.getErrorMessage()).isNotBlank();

        assertThat(jobRepository.findBySourceAndExternalId(JobSource.ARBEITNOW, "partial-slug")).isPresent();
        assertThat(recordingEventListener.received).hasSize(1);
    }

    private ArbeitnowClient.PartialIngestionResult complete(ArbeitnowJobDto... dtos) {
        return new ArbeitnowClient.PartialIngestionResult(List.of(dtos), true, null);
    }

    private ArbeitnowJobDto jobDto(String slug) {
        return new ArbeitnowJobDto(
                slug,
                "Acme GmbH",
                "Software Engineer",
                "<p>Some <strong>HTML</strong> description</p>",
                true,
                "https://www.arbeitnow.com/jobs/companies/acme/" + slug,
                List.of("Remote", "IT"),
                null,
                "Berlin",
                1700000000L
        );
    }

    /**
     * Records every JobIngestedEvent published during a test so we can
     * assert on exactly what was (or wasn't) published, without depending
     * on any real downstream consumer existing yet.
     */
    static class RecordingEventListener {
        final List<JobIngestedEvent> received = new CopyOnWriteArrayList<>();

        @EventListener
        public void onApplicationEvent(JobIngestedEvent event) {
            received.add(event);
        }
    }

    @TestConfiguration
    static class TestListenerConfig {
        @Bean
        @Primary
        RecordingEventListener recordingEventListener() {
            return new RecordingEventListener();
        }
    }
}