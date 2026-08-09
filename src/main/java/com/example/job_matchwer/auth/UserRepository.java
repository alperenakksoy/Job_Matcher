package com.example.job_matchwer.auth;

import lombok.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<@NonNull User,@NonNull UUID> {
    Optional<User> findByEmail(String email);
}