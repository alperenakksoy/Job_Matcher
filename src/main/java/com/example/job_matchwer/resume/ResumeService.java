package com.example.job_matchwer.resume;

import com.example.job_matchwer.auth.User;
import com.example.job_matchwer.auth.UserRepository;
import com.example.job_matchwer.common.exception.DuplicateResourceException;
import com.example.job_matchwer.common.exception.FileSizeException;
import com.example.job_matchwer.common.exception.InvalidFileException;
import com.example.job_matchwer.common.exception.ResourceNotFoundException;
import com.example.job_matchwer.grpc.ParseResumeResponse;
import com.example.job_matchwer.mlclient.MlServiceClient;
import com.google.protobuf.util.JsonFormat;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.time.ZoneId;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ResumeService {

    private static final Logger log = LoggerFactory.getLogger(ResumeService.class);

    private final ResumeRepository resumeRepository;
    private final UserRepository userRepository;
    private final MlServiceClient mlServiceClient;

    private final String UPLOAD_DIR = "storage/resumes";

    @Transactional
    public ResumeResponseDto uploadResume(MultipartFile file, String email) {
        try {
            validatePdf(file);

            // Updated to ResourceNotFoundException
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new ResourceNotFoundException("Kullanıcı bulunamadı"));

            String fileHash = calculateHash(file.getBytes());
            var existingResume = resumeRepository.findByUserEmailAndFileHash(email, fileHash);

            if (existingResume.isPresent()) {
                return mapToDto(existingResume.get());
            }

            UUID uniqueFileId = UUID.randomUUID();
            Path projectRoot = Paths.get(System.getProperty("user.dir"));
            Path userDirectory = projectRoot.resolve(UPLOAD_DIR).resolve(user.getId().toString());

            if (!Files.exists(userDirectory)) {
                Files.createDirectories(userDirectory);
            }

            Path filePath = userDirectory.resolve(uniqueFileId.toString() + ".pdf");
            file.transferTo(filePath.toAbsolutePath().toFile());

            Resume resume = new Resume(
                    user,
                    file.getOriginalFilename(),
                    filePath.toAbsolutePath().toString(),
                    fileHash,
                    file.getSize(),
                    1
            );

            try {
                Resume savedResume = resumeRepository.save(resume);
                return mapToDto(savedResume);
            } catch (DataIntegrityViolationException e) {
                throw new DuplicateResourceException("This CV was just uploaded in a concurrent request.");
            }

        } catch (IOException | NoSuchAlgorithmException e) {
            throw new RuntimeException("Error has been occurred during the upload the file: " + e.getMessage(), e);
        }
    }

    public List<ResumeResponseDto> getUserResumes(String email) {
        return resumeRepository.findAllByUserEmail(email)
                .stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public ResumeResponseDto getUserResumeById(UUID id, String email) {
        Resume resume = resumeRepository.findByIdAndUserEmail(id, email)
                .orElseThrow(() -> new ResourceNotFoundException("CV has not been found or you do not have access."));
        return mapToDto(resume);
    }

    /**
     * Triggers ml-service parsing for an already-uploaded resume and persists
     * the result. Called by the controller right after upload succeeds.
     *
     * Note: this holds one transaction (and its DB connection) open across a
     * synchronous gRPC call, which is fine at today's scale but is exactly
     * the kind of thing Week 6's RabbitMQ move (JobIngested -> ... ->
     * MatchCalculated) exists to get rid of - revisit once parsing moves
     * onto that event chain instead of the request thread.
     */
    @Transactional
    public ResumeResponseDto parseResume(UUID resumeId, String email) {
        Resume resume = resumeRepository.findByIdAndUserEmail(resumeId, email)
                .orElseThrow(() -> new ResourceNotFoundException("CV has not been found or you do not have access."));

        if (resume.getStatus() == ResumeStatus.PARSED) {
            // Same file re-uploaded (dedup-by-hash path in uploadResume) -
            // already parsed, don't burn another ml-service call on it.
            return mapToDto(resume);
        }

        resume.markParsing();

        byte[] fileContent;
        try {
            fileContent = Files.readAllBytes(Path.of(resume.getStoragePath()));
        } catch (IOException e) {
            resume.markFailed("Could not read stored file: " + e.getMessage());
            resumeRepository.save(resume);
            log.error("Failed to read resume file from disk, resumeId={}", resumeId, e);
            return mapToDto(resume);
        }

        ParseResumeResponse response = mlServiceClient.parseResume(
                resumeId.toString(), fileContent, resume.getOriginalFileName());

        if (response.getSuccess()) {
            try {
                String parsedJson = JsonFormat.printer()
                        .omittingInsignificantWhitespace()
                        .print(response.getParsed());
                resume.markParsed(parsedJson);
            } catch (Exception e) {
                // Should not happen for a well-formed proto response, but a parsed
                // resume with unstorable JSON is a bug worth surfacing as FAILED
                // rather than silently keeping stale/empty parsed_data.
                resume.markFailed("Failed to serialize parsed data: " + e.getMessage());
                log.error("Failed to serialize ParsedResume to JSON, resumeId={}", resumeId, e);
            }
        } else {
            resume.markFailed(response.getErrorMessage());
        }

        resumeRepository.save(resume);
        return mapToDto(resume);
    }

    private void validatePdf(MultipartFile file) throws IOException {
        byte[] bytes = file.getBytes();
        if (bytes.length < 5) {
            throw new FileSizeException("Invalid file size.");
        }

        String magicByte = new String(bytes, 0, 5);
        if (!"%PDF-".equals(magicByte)) {
            throw new InvalidFileException("Only PDF files can be uploaded! (Magic Byte could not be confirmed)");
        }
    }

    private String calculateHash(byte[] fileBytes) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hashBytes = digest.digest(fileBytes);

        StringBuilder hexString = new StringBuilder();
        for (byte b : hashBytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    private ResumeResponseDto mapToDto(Resume resume) {
        return ResumeResponseDto.builder()
                .id(resume.getId())
                .originalFileName(resume.getOriginalFileName())
                .size(resume.getFileSizeBytes())
                .status(resume.getStatus())
                .uploadedAt(LocalDateTime.ofInstant(resume.getCreatedAt(), ZoneId.systemDefault()))
                .build();
    }
}