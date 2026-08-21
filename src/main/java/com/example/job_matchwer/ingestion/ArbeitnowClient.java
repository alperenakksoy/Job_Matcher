package com.example.job_matchwer.ingestion;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class ArbeitnowClient {

    private final RestClient restClient;
    private final String baseUrl;

    public ArbeitnowClient(
            RestClient restClient,
            @Value("${integrations.arbeitnow.base-url}") String baseUrl
    ) {
        this.restClient = restClient;
        this.baseUrl = baseUrl;
    }

    public ArbeitnowResponseDto getJobs() {
        try {
            return restClient
                    .get()
                    .uri(baseUrl)
                    .retrieve()
                    .body(ArbeitnowResponseDto.class);
        }catch (RestClientException e) {
            throw new IngestionFetchException("Failed to fetch jobs from Arbeitnow", e);

        }

    }
}