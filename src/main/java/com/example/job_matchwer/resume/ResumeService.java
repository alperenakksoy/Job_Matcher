package com.example.job_matchwer.resume;

import com.example.job_matchwer.auth.User;
import com.example.job_matchwer.auth.UserRepository;
import com.example.job_matchwer.common.exception.FileSizeException;
import com.example.job_matchwer.common.exception.InvalidFileException;
import lombok.RequiredArgsConstructor;
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

    private final ResumeRepository resumeRepository;
    private final UserRepository userRepository;

    private final String UPLOAD_DIR = "storage/resumes";

    @Transactional
    public ResumeResponseDto uploadResume(MultipartFile file, String email) {
        try {
            validatePdf(file);

            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("Kullanıcı bulunamadı"));

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

            Resume savedResume = resumeRepository.save(resume);

            return mapToDto(savedResume);

        } catch (IOException | NoSuchAlgorithmException e) {
            throw new RuntimeException("Error has been occurred during the upload the file: " + e.getMessage());
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
                .orElseThrow(() -> new RuntimeException("CV has not been found or you do not have the access"));
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