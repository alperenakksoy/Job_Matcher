package com.example.job_matchwer.resume;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/resumes")
@RequiredArgsConstructor
public class ResumeController {

    private final ResumeService resumeService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<@NonNull ResumeResponseDto> uploadResume(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserDetails userDetails) {

        // İşlemi servise devrediyoruz, yükleyen kullanıcının e-postasını da iletiyoruz
        ResumeResponseDto response = resumeService.uploadResume(file, userDetails.getUsername());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<@NonNull List<ResumeResponseDto>> getMyResumes(
            @AuthenticationPrincipal UserDetails userDetails) {

        List<ResumeResponseDto> resumes = resumeService.getUserResumes(userDetails.getUsername());

        return ResponseEntity.ok(resumes);
    }

    @GetMapping("/{id}")
    public ResponseEntity<@NonNull ResumeResponseDto> getResumeById(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails) {

        ResumeResponseDto resume = resumeService.getUserResumeById(id, userDetails.getUsername());

        return ResponseEntity.ok(resume);
    }
}