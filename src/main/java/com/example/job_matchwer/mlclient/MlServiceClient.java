package com.example.job_matchwer.mlclient;

import com.example.job_matchwer.grpc.JobMatcherMlServiceGrpc;
import com.example.job_matchwer.grpc.ParseResumeRequest;
import com.example.job_matchwer.grpc.ParseResumeResponse;
import com.google.protobuf.ByteString;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Single entry point Spring code uses to talk to the ml-service over gRPC.
 * ResumeService (and later job-parsing/match code) should depend on this,
 * never on the generated stub directly - that keeps circuit breaking,
 * logging, and any future retry/timeout policy in one place.
 */
@Component
public class MlServiceClient {

    private static final Logger log = LoggerFactory.getLogger(MlServiceClient.class);

    private final JobMatcherMlServiceGrpc.JobMatcherMlServiceBlockingStub stub;

    public MlServiceClient(JobMatcherMlServiceGrpc.JobMatcherMlServiceBlockingStub stub) {
        this.stub = stub;
    }

    /**
     * Calls the ml-service to parse a resume PDF.
     *
     * Wrapped in the "ml-service" circuit breaker (config in application.yml):
     * after enough recent failures it trips OPEN and calls fall straight
     * through to {@link #parseResumeFallback} without hitting the network,
     * so a dead ml-service doesn't pile up hung requests on this side.
     */
    @CircuitBreaker(name = "ml-service", fallbackMethod = "parseResumeFallback")
    public ParseResumeResponse parseResume(String resumeId, byte[] fileContent, String originalFilename) {
        ParseResumeRequest request = ParseResumeRequest.newBuilder()
                .setResumeId(resumeId)
                .setFileContent(ByteString.copyFrom(fileContent))
                .setOriginalFilename(originalFilename)
                .build();

        return stub.parseResume(request);
    }

    /**
     * Fallback signature must mirror the guarded method's params plus a
     * Throwable - Resilience4j matches it by reflection at startup, so
     * getting this signature wrong fails silently until you call it.
     *
     * Any Throwable is caught here: gRPC failures (ml-service down, timeout
     * via TimeLimiter, malformed response) and the CircuitBreaker's own
     * CallNotPermittedException once the breaker is OPEN.
     */
    private ParseResumeResponse parseResumeFallback(String resumeId, byte[] fileContent,
                                                      String originalFilename, Throwable t) {
        if (t instanceof StatusRuntimeException sre) {
            log.error("ml-service parseResume failed for resumeId={}: {}", resumeId, sre.getStatus(), sre);
        } else {
            log.error("ml-service parseResume unavailable for resumeId={}: {}", resumeId, t.getMessage(), t);
        }

        return ParseResumeResponse.newBuilder()
                .setResumeId(resumeId)
                .setSuccess(false)
                .setErrorMessage("ml-service unavailable: " + t.getMessage())
                .build();
    }
}
