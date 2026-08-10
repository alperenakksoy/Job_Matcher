package com.example.job_matchwer.resume;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ResumeRepository extends JpaRepository<Resume, UUID> {

    List<Resume> findAllByUserEmail(String email);
    Optional<Resume> findByIdAndUserEmail(UUID id, String email);
    Optional<Resume> findByUserEmailAndFileHash(String email, String fileHash);
}