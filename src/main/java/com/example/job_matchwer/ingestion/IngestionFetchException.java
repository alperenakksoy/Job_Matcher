package com.example.job_matchwer.ingestion;

public class IngestionFetchException extends RuntimeException {
    public IngestionFetchException(String message, Throwable cause) {
        super(message, cause);
    }
}