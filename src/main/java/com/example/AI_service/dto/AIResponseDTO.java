package com.example.AI_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AIResponseDTO {
    private String intent;
    private String message;
    private List<AIProductDTO> products;
    private CompareResult compareResult;
    private String llmSummary;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompareResult {
        private AIProductDTO product1;
        private AIProductDTO product2;
        private String winner;
        private String reasoning;
    }
}
