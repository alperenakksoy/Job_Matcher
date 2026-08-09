package com.example.job_matchwer.auth;

public record RegisterRequest(
        String email,
        String password
) {}