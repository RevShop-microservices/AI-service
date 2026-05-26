package com.example.AI_service.repository;

import com.example.AI_service.model.*;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends MongoRepository<Review, String> {

    List<Review> findByProductId(String productId);

    Optional<Review> findByProductIdAndUserId(String productId, Long userId);

    void deleteByProductIdAndUserId(String productId, Long userId);
}