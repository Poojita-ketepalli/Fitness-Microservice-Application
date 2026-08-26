package com.fitness.aiservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

@Service
@Slf4j
public class GeminiService {
    private final WebClient webClient;

    @Value("${gemini.api.url}")
    private String geminiAPIUrl;
    @Value("${gemini.api.key}")
    private String geminiAPIKey;

    public GeminiService(WebClient.Builder webClientBuilder){

        this.webClient = webClientBuilder.build();
        //this.webClient = WebClient.create();
    }

    public String getAnswer(String question){
        Map<String,String> requestBody = Map.of(
                "model","gemini-3.1-flash-lite",
                "input",question
        );
        log.info("Sending payload to Interactions API endpoint: {}", geminiAPIUrl);
        try {
            String response = webClient.post()
                    .uri(geminiAPIUrl)
                    .header("x-goog-api-key", geminiAPIKey)
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            return response;
        } catch (Exception e) {
            log.error("Error while calling Gemini API: {}", e.getMessage());
            throw new RuntimeException("Failed to get response from Gemini API", e);
        }
    }
}

