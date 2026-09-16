package com.example.job_matchwer.mlclient;

import com.example.job_matchwer.grpc.JobMatcherMlServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires up the gRPC channel + blocking stub used to talk to the Python
 * ml-service (proto/job_matcher.proto).
 *
 * Blocking stub is used deliberately, not async: resume parsing today is a
 * synchronous step in the upload flow, so a blocking call keeps the calling
 * code simple. Revisit if/when parsing moves fully onto the RabbitMQ event
 * chain (Week 6) and nothing is left waiting on the response.
 */
@Configuration
@EnableConfigurationProperties(MlServiceProperties.class)
public class GrpcClientConfig {

    @Bean(destroyMethod = "shutdown")
    public ManagedChannel mlServiceChannel(MlServiceProperties properties) {
        return ManagedChannelBuilder
                .forAddress(properties.host(), properties.port())
                // No TLS: internal service-to-service traffic on a trusted
                // network. Revisit before this ever leaves localhost/compose.
                .usePlaintext()
                .build();
    }

    @Bean
    public JobMatcherMlServiceGrpc.JobMatcherMlServiceBlockingStub jobMatcherMlServiceBlockingStub(
            ManagedChannel mlServiceChannel) {
        return JobMatcherMlServiceGrpc.newBlockingStub(mlServiceChannel);
    }
}
