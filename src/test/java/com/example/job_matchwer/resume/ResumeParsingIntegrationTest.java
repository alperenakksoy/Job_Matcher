package com.example.job_matchwer.resume;

import com.example.job_matchwer.auth.RegisterRequest;
import com.example.job_matchwer.grpc.ExtractedField;
import com.example.job_matchwer.grpc.ExtractionSource;
import com.example.job_matchwer.grpc.ParseResumeResponse;
import com.example.job_matchwer.grpc.ParsedResume;
import com.example.job_matchwer.mlclient.MlServiceClient;
import com.example.job_matchwer.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers the part of the resume flow that ResumeIntegrationTest predates:
 * the synchronous call to the ml-service after upload (ResumeService#parseResume).
 *
 * MlServiceClient is mocked rather than hitting a real ml-service over gRPC -
 * this is a Spring integration test, not an end-to-end one. It lets us:
 *   - assert PARSED / FAILED behavior deterministically, without a running
 *     Python process or Docker network path to it
 *   - assert the dedup path never calls the mock a second time for the same
 *     file hash, which is the one thing worth an integration-level check
 *     (a unit test on ResumeService would need the same mock anyway, and
 *     wouldn't catch a wiring mistake in the controller's two-call sequence)
 *
 * The circuit breaker itself (Resilience4j opening/closing) is not exercised
 * here - that's a concern of MlServiceClient in isolation, not of this flow,
 * and is easiest to verify manually per the Week 3 checklist (stop
 * ml-service, confirm status: FAILED) since simulating a real gRPC
 * UNAVAILABLE requires a channel, not just a mocked bean.
 */
class ResumeParsingIntegrationTest extends AbstractIntegrationTest {

    private static final String BASE = "/api";

    private static final Path UPLOAD_ROOT = Paths.get(System.getProperty("user.dir"), "storage", "resumes");

    @MockitoBean
    private MlServiceClient mlServiceClient;

    private String registerAndGetToken() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        String password = "Str0ngP@ssword!";

        String response = mockMvc.perform(post(BASE + "/auth/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new RegisterRequest(email, password))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        @SuppressWarnings("unchecked")
        Map<String, String> tokens = objectMapper.readValue(response, Map.class);
        return tokens.get("accessToken");
    }

    private MockMultipartFile validPdf(String filename) {
        byte[] content = ("%PDF-1.4\n%fake pdf content for testing\n" + UUID.randomUUID())
                .getBytes(StandardCharsets.UTF_8);
        return new MockMultipartFile("file", filename, "application/pdf", content);
    }

    private ExtractedField deterministic(String value) {
        return ExtractedField.newBuilder()
                .setValue(value)
                .setConfidence(0.9f)
                .setSource(ExtractionSource.DETERMINISTIC)
                .build();
    }

    @AfterEach
    void cleanupUploadedFiles() throws IOException {
        if (!Files.exists(UPLOAD_ROOT)) {
            return;
        }
        try (var walk = Files.walk(UPLOAD_ROOT)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // best-effort cleanup
                }
            });
        }
    }

    @Test
    void upload_whenMlServiceSucceeds_persistsParsedDataAndReturnsParsed() throws Exception {
        String token = registerAndGetToken();

        ParsedResume parsed = ParsedResume.newBuilder()
                .setEmail(deterministic("jane.doe@example.com"))
                .build();

        when(mlServiceClient.parseResume(anyString(), any(byte[].class), anyString()))
                .thenAnswer(invocation -> ParseResumeResponse.newBuilder()
                        .setResumeId(invocation.getArgument(0))
                        .setSuccess(true)
                        .setParsed(parsed)
                        .build());

        mockMvc.perform(multipart(BASE + "/resumes")
                        .file(validPdf("cv.pdf"))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PARSED"));

        verify(mlServiceClient, times(1))
                .parseResume(anyString(), any(byte[].class), anyString());
    }

    @Test
    void upload_whenMlServiceFails_marksResumeFailedInsteadOf500() throws Exception {
        String token = registerAndGetToken();

        when(mlServiceClient.parseResume(anyString(), any(byte[].class), anyString()))
                .thenAnswer(invocation -> ParseResumeResponse.newBuilder()
                        .setResumeId(invocation.getArgument(0))
                        .setSuccess(false)
                        .setErrorMessage("ml-service unavailable: UNAVAILABLE: io exception")
                        .build());

        mockMvc.perform(multipart(BASE + "/resumes")
                        .file(validPdf("cv.pdf"))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FAILED"));
    }

    @Test
    void upload_sameFileTwice_onlyCallsMlServiceOnce() throws Exception {
        String token = registerAndGetToken();
        MockMultipartFile file = validPdf("cv.pdf");

        when(mlServiceClient.parseResume(anyString(), any(byte[].class), anyString()))
                .thenAnswer(invocation -> ParseResumeResponse.newBuilder()
                        .setResumeId(invocation.getArgument(0))
                        .setSuccess(true)
                        .setParsed(ParsedResume.newBuilder().setEmail(deterministic("a@b.com")).build())
                        .build());

        mockMvc.perform(multipart(BASE + "/resumes")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PARSED"));

        mockMvc.perform(multipart(BASE + "/resumes")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PARSED"));

        // The second upload hits the dedup-by-hash path in ResumeService and
        // should never reach MlServiceClient again - this is the one thing
        // a unit test on ResumeService can't tell us, since it would need to
        // fake the same "already exists" repository behavior anyway.
        verify(mlServiceClient, times(1))
                .parseResume(anyString(), any(byte[].class), anyString());
        Mockito.verifyNoMoreInteractions(mlServiceClient);
    }
}