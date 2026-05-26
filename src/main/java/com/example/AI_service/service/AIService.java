package com.example.AI_service.service;

import com.example.AI_service.dto.*;
import com.example.AI_service.model.*;
import com.example.AI_service.repository.*;
import com.example.AI_service.util.VectorUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class AIService {

    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private ReviewRepository reviewRepository;
    @Autowired
    private ChatHistoryRepository chatRepository;
    @Autowired
    private EmbeddingService embeddingService;
    @Autowired
    private IntentService intentService;

    @Value("${ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    @Value("${ollama.model:phi3:mini}")
    private String ollamaModel;

    @Value("${ollama.embed-model:nomic-embed-text}")
    private String embedModel;

    public AIResponseDTO handleQuery(Long userId, String query) {
        Intent intent = intentService.classify(query);
        AIResponseDTO response;

        switch (intent) {
            case SEARCH        -> response = handleSearch(query);
            case COMPARE       -> response = handleCompare(query);
//            case SUMMARY       -> response = handleSummary(query);
            case RECOMMEND     -> response = handleRecommend(query);
            case PRICE_FILTER  -> response = handlePriceFilter(query);
            case CATEGORY_BROWSE -> response = handleCategoryBrowse(query);
            case ORDER_HELP    -> response = handleOrderHelp(query);
            case GREET         -> response = handleGreet();
            default            -> response = handleUnknown();
        }

        saveChat(userId, query, response.getMessage());
        return response;
    }

    private AIResponseDTO handleGreet() {
        return AIResponseDTO.builder()
                .intent("GREET")
                .message("👋 Hi! I'm **NexShop AI** — your smart shopping assistant!\n\n" +
                        "Here's what I can help you with:\n" +
                        "🔍 **Search** — \"Find me wireless earbuds\"\n" +
                        "⚖️ **Compare** — \"Compare iPhone vs Samsung\"\n" +
                        "💰 **Budget** — \"Laptops under ₹40000\"\n" +
                        "🏆 **Recommend** — \"Best gaming mouse\"\n" +
                        "📦 **Category** — \"Browse electronics\"\n" +
                        "⭐ **Reviews** — \"Summarize headphone reviews\"\n\n" +
                        "What are you looking for today?")
                .build();
    }

    private AIResponseDTO handleSearch(String query) {
        List<Products> products = getSemanticResults(query, 5);
        List<AIProductDTO> dtos = toDTO(products);

        String msg = products.isEmpty()
                ? "😕 No products found matching your query. Try different keywords!"
                : "🔍 Found **" + products.size() + " products** matching your search:";

        return AIResponseDTO.builder()
                .intent("SEARCH")
                .message(msg)
                .products(dtos)
                .build();
    }

    private AIResponseDTO handleCompare(String query) {
        // Try to extract two product names from "compare X and Y" / "X vs Y"
        List<String> terms = extractCompareTerms(query);
        List<Products> all = productRepository.findAll();

        Products p1 = null, p2 = null;

        if (terms.size() >= 2) {
            // Try exact keyword match first
            p1 = findBestMatch(all, terms.get(0));
            p2 = findBestMatch(all, terms.get(1));
        }

        // Fallback: semantic search for top 2
        if (p1 == null || p2 == null) {
            List<Products> top = getSemanticResults(query, 2);
            if (top.size() >= 2) {
                p1 = top.get(0);
                p2 = top.get(1);
            }
        }

        if (p1 == null || p2 == null) {
            return AIResponseDTO.builder()
                    .intent("COMPARE")
                    .message("❌ Couldn't find enough products to compare. Try: \"Compare iPhone and Samsung Galaxy\"")
                    .build();
        }

        // Generate comparison summary via LLM
        String prompt = buildComparePrompt(p1, p2);
        String llmText = callLLM(prompt);

        // Determine winner by price/stock heuristic
        String winner = p1.getPrice() <= p2.getPrice() ? p1.getName() : p2.getName();

        AIResponseDTO.CompareResult compare = AIResponseDTO.CompareResult.builder()
                .product1(toSingleDTO(p1))
                .product2(toSingleDTO(p2))
                .winner(winner)
                .reasoning(llmText)
                .build();

        return AIResponseDTO.builder()
                .intent("COMPARE")
                .message("⚖️ Comparing **" + p1.getName() + "** vs **" + p2.getName() + "**")
                .compareResult(compare)
                .llmSummary(llmText)
                .build();
    }

//    private AIResponseDTO handleSummary(String query) {
//        List<Products> candidates = getSemanticResults(query, 1);
//        if (candidates.isEmpty()) {
//            return AIResponseDTO.builder().intent("SUMMARY")
//                    .message("❌ No product found to summarize. Try specifying a product name.")
//                    .build();
//        }
//
//        Products product = candidates.get(0);
//        List<Review> reviews = reviewRepository.findByProductId(product.getId());
//
//        if (reviews.isEmpty()) {
//            return AIResponseDTO.builder().intent("SUMMARY")
//                    .message("📦 Found **" + product.getName() + "** but no reviews yet!")
//                    .products(List.of(toSingleDTO(product)))
//                    .build();
//        }
//
//        String combined = reviews.stream()
//                .map(Review::getComment)
//                .collect(Collectors.joining(" | "));
//
////        double avgRating = reviews.stream()
////                .mapToDouble(r -> r.getRating() != null ? r.getRating() : 0)
////                .average().orElse(0);
//
//        String prompt = "You are a product review summarizer. Summarize these customer reviews for "
//                + product.getName() + " in 2-3 sentences highlighting pros and cons. Reviews: " + combined;
//        String summary = callLLM(prompt);
//
//        return AIResponseDTO.builder()
//                .intent("SUMMARY")
//                .message("⭐ Review summary for **" + product.getName() + "** (avg rating: " +
//                        String.format("%.1f", avgRating) + "/5, " + reviews.size() + " reviews):")
//                .products(List.of(toSingleDTO(product)))
//                .llmSummary(summary)
//                .build();
//    }

    private AIResponseDTO handleRecommend(String query) {
        // Use LLM to extract the category/use-case being requested
        List<Products> candidates = getSemanticResults(query, 6);

        // Sort by availability (stock > 0)
        List<Products> inStock = candidates.stream()
                .filter(p -> p.getStock() != null && p.getStock() > 0)
                .limit(4)
                .collect(Collectors.toList());

        if (inStock.isEmpty()) inStock = candidates.stream().limit(4).collect(Collectors.toList());

        String msg = inStock.isEmpty()
                ? "😕 No recommendations found for your query."
                : "🏆 Here are the **top recommendations** for you:";

        return AIResponseDTO.builder()
                .intent("RECOMMEND")
                .message(msg)
                .products(toDTO(inStock))
                .build();
    }

    private AIResponseDTO handlePriceFilter(String query) {
        double maxPrice = extractMaxPrice(query);
        String lowerQuery = query.toLowerCase();

        List<Products> all = productRepository.findAll();

        // Filter by price
        List<Products> filtered = all.stream()
                .filter(p -> p.getPrice() != null && p.getPrice() <= maxPrice)
                .collect(Collectors.toList());

        // If there's a category/keyword hint, re-rank semantically
        if (!filtered.isEmpty()) {
            try {
                List<Double> embedding = embeddingService.getEmbedding(query);
                filtered = filtered.stream()
                        .sorted((a, b) -> Double.compare(similarity(b, embedding), similarity(a, embedding)))
                        .limit(5)
                        .collect(Collectors.toList());
            } catch (Exception ignored) {
                filtered = filtered.stream().limit(5).collect(Collectors.toList());
            }
        }

        String priceLabel = maxPrice == Double.MAX_VALUE ? "any budget" : "under ₹" + (int) maxPrice;
        String msg = filtered.isEmpty()
                ? "😕 No products found " + priceLabel + ". Try increasing your budget!"
                : "💰 **" + filtered.size() + " products** found " + priceLabel + ":";

        return AIResponseDTO.builder()
                .intent("PRICE_FILTER")
                .message(msg)
                .products(toDTO(filtered))
                .build();
    }

    private AIResponseDTO handleCategoryBrowse(String query) {
        List<Products> all = productRepository.findAll();

        // Get distinct categories
        List<String> categories = all.stream()
                .map(Products::getCategory)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .collect(Collectors.toList());

        // Check if a specific category is mentioned
        String specificCat = categories.stream()
                .filter(c -> query.toLowerCase().contains(c.toLowerCase()))
                .findFirst().orElse(null);

        if (specificCat != null) {
            List<Products> catProducts = all.stream()
                    .filter(p -> specificCat.equalsIgnoreCase(p.getCategory()))
                    .limit(5)
                    .collect(Collectors.toList());
            return AIResponseDTO.builder()
                    .intent("CATEGORY_BROWSE")
                    .message("📂 Showing **" + catProducts.size() + " products** in **" + specificCat + "**:")
                    .products(toDTO(catProducts))
                    .build();
        }

        String catList = categories.stream()
                .map(c -> "• " + c)
                .collect(Collectors.joining("\n"));

        return AIResponseDTO.builder()
                .intent("CATEGORY_BROWSE")
                .message("📂 **Available Categories:**\n" + catList + "\n\nTry: \"Show me Electronics\" or \"Browse Clothing\"")
                .build();
    }

    private AIResponseDTO handleOrderHelp(String query) {
        return AIResponseDTO.builder()
                .intent("ORDER_HELP")
                .message("📦 **Order Help**\n\n" +
                        "Here's what you can do:\n" +
                        "• **Track your order** → Go to *My Orders* page\n" +
                        "• **Cancel an order** → Cancel before it ships from *My Orders*\n" +
                        "• **Return/Refund** → Contact support within 7 days of delivery\n" +
                        "• **Delivery time** → Usually 3-7 business days\n\n" +
                        "Need more help? Go to 👉 [My Orders](/orders)")
                .build();
    }

    private AIResponseDTO handleUnknown() {
        return AIResponseDTO.builder()
                .intent("UNKNOWN")
                .message("🤔 I didn't quite understand that. Try:\n" +
                        "• \"Find me a gaming laptop\"\n" +
                        "• \"Compare iPhone and Samsung\"\n" +
                        "• \"Best headphones under ₹5000\"\n" +
                        "• \"Summarize reviews for boAt earphones\"\n" +
                        "• \"Show electronics category\"")
                .build();
    }


    private List<Products> getSemanticResults(String query, int limit) {
        String lq = query.toLowerCase();
        List<Products> all = productRepository.findAll();

        // Keyword pre-filter
        List<Products> filtered = all.stream()
                .filter(p -> {
                    String name = p.getName() != null ? p.getName().toLowerCase() : "";
                    String cat = p.getCategory() != null ? p.getCategory().toLowerCase() : "";
                    String brand = p.getBrand() != null ? p.getBrand().toLowerCase() : "";
                    String desc = p.getDescription() != null ? p.getDescription().toLowerCase() : "";
                    String tags = p.getTags() != null ? String.join(" ", p.getTags()).toLowerCase() : "";
                    return name.contains(lq) || cat.contains(lq) || brand.contains(lq)
                            || desc.contains(lq) || tags.contains(lq)
                            || lq.contains(name) || lq.contains(cat) || lq.contains(brand);
                })
                .collect(Collectors.toList());

        if (filtered.isEmpty()) filtered = all;

        // Semantic re-ranking
        try {
            List<Double> embedding = embeddingService.getEmbedding(query);
            return filtered.stream()
                    .sorted((a, b) -> Double.compare(similarity(b, embedding), similarity(a, embedding)))
                    .limit(limit)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            // If embedding service is down, just return keyword matches
            return filtered.stream().limit(limit).collect(Collectors.toList());
        }
    }

    private Products findBestMatch(List<Products> all, String term) {
        return all.stream()
                .filter(p -> {
                    String t = term.toLowerCase();
                    return (p.getName() != null && p.getName().toLowerCase().contains(t))
                            || (p.getBrand() != null && p.getBrand().toLowerCase().contains(t))
                            || (p.getCategory() != null && p.getCategory().toLowerCase().contains(t));
                })
                .findFirst().orElse(null);
    }

    private List<String> extractCompareTerms(String query) {
        query = query.toLowerCase()
                .replace("compare", "").replace("versus", "vs")
                .replace("difference between", "vs").replace("which is better", "vs");

        // Split on "vs", "and", "or"
        String[] parts = query.split("\\s+vs\\.?\\s+|\\s+and\\s+|\\s+or\\s+");
        List<String> terms = new ArrayList<>();
        for (String p : parts) {
            String t = p.trim().replaceAll("[^a-z0-9 ]", "").trim();
            if (!t.isEmpty()) terms.add(t);
        }
        return terms;
    }

    private double extractMaxPrice(String query) {
        // Match patterns like "under ₹50000", "below 40000", "less than 30000"
        Matcher m = Pattern.compile("(?:under|below|less than|upto|up to)[\\s₹rs.]*([0-9,]+)")
                .matcher(query.toLowerCase());
        if (m.find()) {
            return Double.parseDouble(m.group(1).replace(",", ""));
        }
        // Match "5000 rupees", "50k budget"
        Matcher m2 = Pattern.compile("([0-9]+)k").matcher(query.toLowerCase());
        if (m2.find()) {
            return Double.parseDouble(m2.group(1)) * 1000;
        }
        Matcher m3 = Pattern.compile("([0-9,]+)\\s*(?:rupees|rs|inr)").matcher(query.toLowerCase());
        if (m3.find()) {
            return Double.parseDouble(m3.group(1).replace(",", ""));
        }
        return Double.MAX_VALUE; // No budget found
    }

    private String buildComparePrompt(Products p1, Products p2) {
        return "You are a product expert. Compare these two products in 3-4 sentences. Be concise and helpful.\n\n" +
                "Product 1: " + p1.getName() + " | Brand: " + p1.getBrand() + " | Price: ₹" + p1.getPrice() +
                " | Category: " + p1.getCategory() + " | Description: " + p1.getDescription() + "\n\n" +
                "Product 2: " + p2.getName() + " | Brand: " + p2.getBrand() + " | Price: ₹" + p2.getPrice() +
                " | Category: " + p2.getCategory() + " | Description: " + p2.getDescription() + "\n\n" +
                "Give a balanced comparison and mention which is better value for money.";
    }

    private double similarity(Products p, List<Double> queryEmbedding) {
        if (p.getEmbedding() == null) return 0;
        return VectorUtils.cosineSimilarity(p.getEmbedding(), queryEmbedding);
    }

    // ─── LLM Call (Ollama) ───────────────────────────────────────────────────
    private String callLLM(String prompt) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            String url = ollamaBaseUrl + "/api/generate";

            Map<String, Object> body = new HashMap<>();
            body.put("model", ollamaModel);
            body.put("prompt", prompt);
            body.put("stream", false);
            body.put("options", Map.of("temperature", 0.7, "num_predict", 200));

            Map response = restTemplate.postForObject(url, body, Map.class);
            if (response != null && response.get("response") != null)
                return response.get("response").toString().trim();
        } catch (Exception e) {
            // LLM unavailable — graceful degradation
        }
        return "AI model is currently offline. Please ensure Ollama is running with: `ollama run " + ollamaModel + "`";
    }

    // ─── DTO Mappers ─────────────────────────────────────────────────────────
    private List<AIProductDTO> toDTO(List<Products> products) {
        return products.stream().map(this::toSingleDTO).collect(Collectors.toList());
    }

    private AIProductDTO toSingleDTO(Products p) {
        return AIProductDTO.builder()
                .id(p.getId())
                .name(p.getName())
                .brand(p.getBrand())
                .category(p.getCategory())
                .price(p.getPrice())
                .stock(p.getStock())
                .description(p.getDescription())
                .images(p.getImages())
                .build();
    }

    // ─── Chat History ────────────────────────────────────────────────────────
    private void saveChat(Long userId, String query, String response) {
        try {
            ChatHistory chat = ChatHistory.builder()
                    .userId(userId)
                    .query(query)
                    .response(response)
                    .timestamp(LocalDateTime.now())
                    .build();
            chatRepository.save(chat);
        } catch (Exception ignored) {}
    }
}
