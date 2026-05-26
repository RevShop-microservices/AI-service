package com.example.AI_service.repository;

import com.example.AI_service.model.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductRepository extends MongoRepository<Products, String> {

    int countBySellerId(Long sellerId);

    int countBySellerIdAndStockLessThan(Long sellerId, int stock);

    Page<Products> findByNameContainingIgnoreCase(String name, Pageable pageable);

    Page<Products> findByCategory(String category, Pageable pageable);

    Page<Products> findByBrand(String brand, Pageable pageable);

    Page<Products> findByPriceBetween(Double minPrice, Double maxPrice, Pageable pageable);

    Page<Products> findByCategoryAndBrandAndPriceBetween(String category, String brand, Double minPrice, Double maxPrice, Pageable pageable);

    Page<Products> findByCategoryAndPriceBetween(String category, Double minPrice, Double maxPrice, Pageable pageable);

    Page<Products> findByBrandAndPriceBetween(String brand, Double minPrice, Double maxPrice, Pageable pageable);

    Page<Products> findByTagsContaining(String tag, Pageable pageable);

    Page<Products> findByStockGreaterThan(int stock, Pageable pageable);

    Page<Products> findBySellerId(Long sellerId, Pageable pageable);
}