package com.example.job_matchwer.support;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for full-stack integration tests.
 *
 * Boots the real Spring context against a real Postgres (with pgvector,
 * since our Flyway migration does `CREATE EXTENSION vector`) and a real
 * Redis, both started in Docker via Testcontainers. This is deliberate:
 * H2 cannot run our Flyway migration (jsonb + vector extension), and a
 * fake/mocked Redis would hide bugs in the login-attempt lockout logic
 * in AuthService.
 *
 * Every test class that needs the full application context should extend
 * this instead of repeating the container + MockMvc wiring.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
public abstract class AbstractIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("pgvector/pgvector:pg16")
                    .asCompatibleSubstituteFor("postgres"));

    @Container
    @ServiceConnection(name = "redis")
    static GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @BeforeAll
    static void containersRunning() {
        // Fail fast with a clear message instead of a wall of Spring
        // context-startup noise if Docker isn't available.
        if (!postgres.isRunning()) {
            throw new IllegalStateException(
                    "Testcontainers Postgres did not start. Is Docker Desktop running?");
        }
    }
}