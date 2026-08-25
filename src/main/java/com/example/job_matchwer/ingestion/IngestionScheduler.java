package com.example.job_matchwer.ingestion;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically triggers job ingestion. Wrapped in a ShedLock so that if this
 * service is ever scaled to multiple instances, only one instance actually
 * runs the job on a given tick - the others see the lock is held and skip.
 *
 * The manual POST /ingestion/trigger endpoint is left in place for on-demand
 * runs (debugging, admin panel, etc). Because IngestionService#runIngestion
 * is already safe under concurrent invocation (unique constraint on
 * (source, external_id) + DataIntegrityViolationException handling), a manual
 * trigger racing with a scheduled tick will not create duplicate jobs even
 * though ShedLock only guards the scheduled path.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IngestionScheduler {

    private final IngestionService ingestionService;

    @Scheduled(cron = "${ingestion.arbeitnow.cron}")
    @SchedulerLock(name = "arbeitnow-ingestion", lockAtLeastFor = "30s", lockAtMostFor = "5m")
    public void runArbeitnowIngestion() {
        log.info("Scheduled Arbeitnow ingestion starting");
        IngestionRun run = ingestionService.runIngestion();
        log.info(
                "Scheduled Arbeitnow ingestion finished: status={} fetched={} created={} skipped={}",
                run.getStatus(), run.getJobsFetched(), run.getJobsCreated(), run.getJobsSkipped()
        );
    }
}

