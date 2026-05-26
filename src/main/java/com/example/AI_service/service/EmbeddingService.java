package com.example.AI_service.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class EmbeddingService {

    @Value("${ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    @Value("${ollama.embed-model:nomic-embed-text}")
    private String embedModel;

    private final RestTemplate restTemplate = new RestTemplate();

    public List<Double> getEmbedding(String text) {
        try {
            String url = ollamaBaseUrl + "/api/embeddings";
            Map<String, Object> body = new HashMap<>();
            body.put("model", embedModel);
            body.put("prompt", text);

            Map response = restTemplate.postForObject(url, body, Map.class);
            if (response != null && response.get("embedding") != null) {
                return (List<Double>) response.get("embedding");
            }
            throw new RuntimeException("Empty embedding response");
        } catch (Exception e) {
            throw new RuntimeException("AI embedding service unavailable: " + e.getMessage());
        }
    }
}
