package com.example.job_matchwer.auth;

public record LoginRequest(
        String email,
        String password
) {}