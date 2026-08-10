package com.example.job_matchwer.resume;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
public class ResumeResponseDto {
    private UUID id;
    private String originalFileName;
    private Long size;
    private ResumeStatus status; // UPLOADED vb.
    private LocalDateTime uploadedAt;
}