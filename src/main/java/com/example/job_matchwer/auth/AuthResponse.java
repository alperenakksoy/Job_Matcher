package com.example.job_matchwer.auth;

public record AuthResponse(
        String accessToken,
        String refreshToken
) {}