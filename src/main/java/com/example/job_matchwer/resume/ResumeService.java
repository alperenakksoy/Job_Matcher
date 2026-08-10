package com.example.job_matchwer.resume;

import com.example.job_matchwer.auth.User;
import com.example.job_matchwer.auth.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

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

    // Dosyaların kaydedileceği ana dizin
    private final String UPLOAD_DIR = "storage/resumes";

    @Transactional
    public ResumeResponseDto uploadResume(MultipartFile file, String email) {
        try {
            // 1. PDF Magic Byte Kontrolü
            validatePdf(file);

            // 2. Kullanıcıyı bul
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("Kullanıcı bulunamadı"));

            // 3. SHA-256 Hash Hesapla ve Çakışma Kontrolü Yap
            String fileHash = calculateHash(file.getBytes());
            var existingResume = resumeRepository.findByUserEmailAndFileHash(email, fileHash);

            if (existingResume.isPresent()) {
                // Dosya zaten yüklenmiş, veritabanına yeniden yazmadan mevcut kaydı dön
                return mapToDto(existingResume.get());
            }

            // 4. Dosyayı Diske Yaz
            // Sadece fiziksel dosya adı için rastgele bir UUID üretiyoruz
            UUID uniqueFileId = UUID.randomUUID();
            Path userDirectory = Paths.get(UPLOAD_DIR, user.getId().toString());

            // Kullanıcı için klasör yoksa oluştur
            if (!Files.exists(userDirectory)) {
                Files.createDirectories(userDirectory);
            }

            // Dosyayı diske yaz (Örn: storage/resumes/kullanici_id/rastgele-uuid.pdf)
            Path filePath = userDirectory.resolve(uniqueFileId.toString() + ".pdf");
            file.transferTo(filePath.toFile());

            // 5. Veritabanına Kaydet (Oluşturduğun Constructor'ı kullanıyoruz)
            Resume resume = new Resume(
                    user,
                    file.getOriginalFilename(),
                    filePath.toString(), // Entity'deki storagePath alanı
                    fileHash,
                    file.getSize(),      // Entity'deki fileSizeBytes alanı
                    1                    // default version
            );

            // ID atamasını Spring Data JPA (Hibernate) otomatik yapacak
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

    // --- YARDIMCI METOTLAR ---

    private void validatePdf(MultipartFile file) throws IOException {
        byte[] bytes = file.getBytes();
        if (bytes.length < 5) {
            throw new RuntimeException("invalid size of file");
        }

        // %PDF- magic byte kontrolü
        String magicByte = new String(bytes, 0, 5);
        if (!"%PDF-".equals(magicByte)) {
            throw new RuntimeException("Only PDF files can be uploaded! (Magic Byte could not be confirmed)");
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
                .size(resume.getFileSizeBytes()) // Entity'deki fileSizeBytes alanını eşleştirdik
                .status(resume.getStatus())
                .uploadedAt(LocalDateTime.from(resume.getCreatedAt()))
                .build();
    }
}