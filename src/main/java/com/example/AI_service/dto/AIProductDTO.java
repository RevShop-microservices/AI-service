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
public class AIProductDTO {
    private String id;
    private String name;
    private String brand;
    private String category;
    private Double price;
    private Integer stock;
    private String description;
    private List<String> images;
    private Double similarityScore;
}
