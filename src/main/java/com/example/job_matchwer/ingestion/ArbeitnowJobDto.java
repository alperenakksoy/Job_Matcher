package com.example.job_matchwer.ingestion;

import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.JsonNode;

import java.util.List;

public record ArbeitnowJobDto(
        String slug,
        @JsonProperty("company_name") String companyName,
        String title,
        String description,
        boolean remote,
        String url,
        List<String> tags,
        @JsonProperty("job_types") JsonNode jobTypes,
        String location,
        @JsonProperty("created_at") long createdAt
) {}