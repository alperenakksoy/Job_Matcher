package com.example.job_matchwer.auth;

import java.time.Instant;
import java.util.UUID;

public record UserDto(
        UUID id,
        String email,
        String role,
        boolean enabled,
        Instant createdAt
) {}