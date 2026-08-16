package com.example.job_matchwer.resume;

import com.example.job_matchwer.auth.RegisterRequest;
import com.example.job_matchwer.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ResumeIntegrationTest extends AbstractIntegrationTest {

    private static final String BASE = "/api";

    // ResumeService currently hard-codes "storage/resumes" relative to the
    // process working directory, ignoring app.storage.resume-path. Tests
    // write into the real project tree because of that, so we clean up
    // everything created under it after each test.
    private static final Path UPLOAD_ROOT = Paths.get(System.getProperty("user.dir"), "storage", "resumes");

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
    void upload_withoutToken_returns401() throws Exception {
        mockMvc.perform(multipart(BASE + "/resumes").file(validPdf("cv.pdf")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void upload_validPdf_returns201AndPersists() throws Exception {
        String token = registerAndGetToken();

        mockMvc.perform(multipart(BASE + "/resumes")
                        .file(validPdf("cv.pdf"))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.originalFileName").value("cv.pdf"))
                .andExpect(jsonPath("$.status").value("UPLOADED"));
    }

    @Test
    void upload_sameFileTwice_returnsSameResumeAndDoesNotDuplicate() throws Exception {
        String token = registerAndGetToken();
        MockMultipartFile file = validPdf("cv.pdf");

        String firstResponse = mockMvc.perform(multipart(BASE + "/resumes")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String secondResponse = mockMvc.perform(multipart(BASE + "/resumes")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        @SuppressWarnings("unchecked")
        Map<String, Object> first = objectMapper.readValue(firstResponse, Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> second = objectMapper.readValue(secondResponse, Map.class);

        org.assertj.core.api.Assertions.assertThat(second.get("id")).isEqualTo(first.get("id"));

        // Confirm only one resume row exists for this user, not two.
        mockMvc.perform(get(BASE + "/resumes").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void upload_nonPdfFile_returns400() throws Exception {
        String token = registerAndGetToken();
        MockMultipartFile notAPdf = new MockMultipartFile(
                "file", "cv.txt", "text/plain", "just some text".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart(BASE + "/resumes")
                        .file(notAPdf)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getResumeById_forAnotherUsersResume_isNotAccessible() throws Exception {
        String ownerToken = registerAndGetToken();
        String otherUserToken = registerAndGetToken();

        String uploadResponse = mockMvc.perform(multipart(BASE + "/resumes")
                        .file(validPdf("cv.pdf"))
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        @SuppressWarnings("unchecked")
        Map<String, Object> resume = objectMapper.readValue(uploadResponse, Map.class);
        String resumeId = (String) resume.get("id");

        mockMvc.perform(get(BASE + "/resumes/" + resumeId)
                        .header("Authorization", "Bearer " + otherUserToken))
                .andExpect(result -> {
                    int sc = result.getResponse().getStatus();
                    if (sc != 403 && sc != 404) {
                        throw new AssertionError(
                                "Expected 403 or 404 accessing another user's resume, got " + sc);
                    }
                });
    }

    @Test
    void getResumeById_forOwnResume_returns200() throws Exception {
        String token = registerAndGetToken();

        String uploadResponse = mockMvc.perform(multipart(BASE + "/resumes")
                        .file(validPdf("cv.pdf"))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        @SuppressWarnings("unchecked")
        Map<String, Object> resume = objectMapper.readValue(uploadResponse, Map.class);
        String resumeId = (String) resume.get("id");

        mockMvc.perform(get(BASE + "/resumes/" + resumeId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(resumeId));
    }

    @Test
    void everyResponse_includesCorrelationIdHeader() throws Exception {
        String token = registerAndGetToken();

        mockMvc.perform(get(BASE + "/resumes").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(result ->
                        org.assertj.core.api.Assertions
                                .assertThat(result.getResponse().getHeader("X-Correlation-Id"))
                                .isNotBlank());
    }
}