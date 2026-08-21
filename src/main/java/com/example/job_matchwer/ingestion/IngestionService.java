package com.example.job_matchwer.ingestion;

import com.example.job_matchwer.job.Job;
import com.example.job_matchwer.job.JobSource;
import com.example.job_matchwer.job.RemoteType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class IngestionService {

    private Job mapToJob(ArbeitnowJobDto dto, ObjectMapper objectMapper) {
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
}
