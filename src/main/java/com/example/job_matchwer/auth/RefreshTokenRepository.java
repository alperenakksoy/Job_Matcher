package com.example.job_matchwer.auth;

import lombok.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<@NonNull RefreshToken, @NonNull UUID> {
    Optional<RefreshToken> findByToken(String token);
}