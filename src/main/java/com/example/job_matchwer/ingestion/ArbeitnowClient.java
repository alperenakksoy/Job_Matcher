package com.example.job_matchwer.ingestion;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class ArbeitnowClient {

    // Arbeitnow doesn't document a page cap; this is purely a circuit
    // breaker against an accidental infinite loop if "next" ever pointed
    // back at itself or the response shape changed unexpectedly.
    private static final int MAX_PAGES = 50;

    // Fetching pages back-to-back with no delay trips Arbeitnow's Cloudflare
    // bot protection (observed: 429 with a Cloudflare challenge page around
    // page 10-11). A small delay between requests keeps us under whatever
    // threshold triggers that.
    private static final long DELAY_BETWEEN_PAGES_MS = 400;

    private final RestClient restClient;
    private final String baseUrl;

    public ArbeitnowClient(
            RestClient restClient,
            @Value("${integrations.arbeitnow.base-url}") String baseUrl
    ) {
        this.restClient = restClient;
        this.baseUrl = baseUrl;
    }

    /**
     * Fetches a single page. Kept public because it's the simplest unit to
     * test against and to call directly if a caller ever needs just the
     * first page.
     */
    public ArbeitnowResponseDto getJobs() {
        return getJobs(baseUrl);
    }

    private ArbeitnowResponseDto getJobs(String url) {
        try {
            return restClient
                    .get()
                    .uri(url)
                    .retrieve()
                    .body(ArbeitnowResponseDto.class);
        } catch (RestClientException e) {
            throw new IngestionFetchException("Failed to fetch jobs from Arbeitnow", e);
        }
    }

    /**
     * Fetches every page of the job board, following the "links.next" URL
     * (when the API includes one) until there is no next page or the safety
     * cap is hit. If the API never returns a "links" object at all (as
     * observed for the current dataset size), this is equivalent to a
     * single getJobs() call.
     *
     * If a page fails partway through (e.g. rate limiting), the jobs already
     * collected from earlier pages are not discarded - they're wrapped in a
     * PartialIngestionResult so the caller can decide what to do with them,
     * instead of the whole run losing everything it had already fetched.
     */
    public PartialIngestionResult getAllJobs() {
        List<ArbeitnowJobDto> allJobs = new ArrayList<>();
        String nextUrl = baseUrl;
        int pagesFetched = 0;

        while (nextUrl != null && pagesFetched < MAX_PAGES) {
            ArbeitnowResponseDto page;
            try {
                page = getJobs(nextUrl);
            } catch (IngestionFetchException e) {
                log.warn(
                        "Arbeitnow pagination failed on page {} after collecting {} jobs from earlier pages; returning partial results",
                        pagesFetched + 1, allJobs.size(), e
                );
                return new PartialIngestionResult(allJobs, false, e);
            }

            allJobs.addAll(page.data());
            pagesFetched++;

            nextUrl = (page.links() != null) ? page.links().next() : null;

            if (nextUrl != null && pagesFetched < MAX_PAGES) {
                sleepBetweenPages();
            }
        }

        if (pagesFetched == MAX_PAGES && nextUrl != null) {
            log.warn("Arbeitnow pagination hit the safety cap of {} pages; some jobs may not have been fetched", MAX_PAGES);
        }

        return new PartialIngestionResult(allJobs, true, null);
    }

    private void sleepBetweenPages() {
        try {
            Thread.sleep(DELAY_BETWEEN_PAGES_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IngestionFetchException("Interrupted while pacing Arbeitnow pagination requests", e);
        }
    }

    /**
     * Result of a getAllJobs() call. "complete" is false when pagination
     * stopped early due to an error (e.g. rate limiting) - "jobs" still
     * contains whatever was successfully collected before that happened,
     * and "failure" carries the underlying cause for logging.
     */
    public record PartialIngestionResult(List<ArbeitnowJobDto> jobs, boolean complete, Exception failure) {}
}
