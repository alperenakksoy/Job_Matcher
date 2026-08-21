package com.example.job_matchwer;

import com.example.job_matchwer.ingestion.ArbeitnowClient;
import com.example.job_matchwer.ingestion.ArbeitnowResponseDto;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

public class ArbeitnowClientManualCheck {
    public static void main(String[] args) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(10));

        RestClient restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();

        ArbeitnowClient client = new ArbeitnowClient(
                restClient,
                "https://www.arbeitnow.com/api/job-board-api"
        );

        ArbeitnowResponseDto response = client.getJobs();
        System.out.println("Total jobs fetched: " + response.data().size());
        response.data().stream().limit(3).forEach(job ->
                System.out.println(job.title() + " @ " + job.companyName() + " (" + job.slug() + ")")
        );
    }
}