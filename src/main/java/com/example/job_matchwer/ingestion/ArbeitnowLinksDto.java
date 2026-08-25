package com.example.job_matchwer.ingestion;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ArbeitnowLinksDto(
        @JsonProperty("next") String next
) {}
