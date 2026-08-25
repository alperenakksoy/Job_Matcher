package com.example.job_matchwer.ingestion;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * The Arbeitnow API may or may not include a "links" object with a "next"
 * URL depending on how many results there are. When present, we follow it
 * (see ArbeitnowClient#getAllJobs) so that ingestion isn't silently limited
 * to whatever the first page happens to contain. "links" is nullable/absent
 * on purpose - if the API never paginates in practice, we still work fine
 * with a single page.
 */
public record ArbeitnowResponseDto(
        List<ArbeitnowJobDto> data,
        @JsonProperty("links") ArbeitnowLinksDto links
) {}
