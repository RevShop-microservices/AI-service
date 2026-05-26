package com.example.AI_service.repository;

import com.example.AI_service.model.*;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ChatHistoryRepository extends MongoRepository<ChatHistory, String> {
    List<ChatHistory> findByUserId(Long userId);
}
