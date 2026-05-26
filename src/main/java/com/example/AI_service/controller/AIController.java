package com.example.AI_service.controller;

import com.example.AI_service.dto.AIResponseDTO;
import com.example.AI_service.service.AIService;
import com.example.AI_service.service.EmbeddingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ai")
@Tag(name = "AI Controller", description = "AI-powered product search, comparison, recommendations")
public class AIController {

    @Autowired
    private AIService service;

    @Autowired
    private EmbeddingService embeddingService;

    @PostMapping("/query")
    @Operation(summary = "Process a natural language query",
               description = "Supports search, compare, recommend, price filter, category browse, review summary")
    public ResponseEntity<AIResponseDTO> query(
            @RequestParam(defaultValue = "0") Long userId,
            @RequestBody String query) {
        return ResponseEntity.ok(service.handleQuery(userId, query));
    }

    @PostMapping("/embeddings")
    @Operation(summary = "Generate vector embeddings for text")
    public ResponseEntity<List<Double>> getEmbeddings(@RequestBody String text) {
        return ResponseEntity.ok(embeddingService.getEmbedding(text));
    }

    @GetMapping("/health")
    @Operation(summary = "Check AI service health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("{\"status\":\"AI service running\",\"model\":\"phi3:mini\"}");
    }
}

