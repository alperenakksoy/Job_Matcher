package com.example.job_matchwer.ingestion;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/ingestion")
@RequiredArgsConstructor
public class IngestionController {

    private final IngestionService ingestionService;
    private final IngestionRunRepository ingestionRunRepository;

    @PostMapping("/trigger")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<@NonNull IngestionRunResponseDto> trigger() {
        IngestionRun run = ingestionService.runIngestion();
        return ResponseEntity.ok(toDto(run));
    }

    @GetMapping("/runs")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<@NonNull List<IngestionRunResponseDto>> getRuns() {
        List<IngestionRunResponseDto> runs = ingestionRunRepository.findAll()
                .stream()
                .map(this::toDto)
                .toList();
        return ResponseEntity.ok(runs);
    }

    private IngestionRunResponseDto toDto(IngestionRun run) {
        return new IngestionRunResponseDto(
                run.getId(),
                run.getSource(),
                run.getStartedAt(),
                run.getCompletedAt(),
                run.getJobsFetched(),
                run.getJobsCreated(),
                run.getJobsSkipped(),
                run.getStatus(),
                run.getErrorMessage()
        );
    }
}