package com.example.job_matchwer.mlclient;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ml-service.grpc")
public record MlServiceProperties(String host, int port) {
}