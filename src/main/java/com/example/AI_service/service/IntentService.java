package com.example.AI_service.service;

import com.example.AI_service.model.Intent;
import org.springframework.stereotype.Service;

@Service
public class IntentService {

    public Intent classify(String query) {
        String q = query.toLowerCase();

        // Greet
        if (q.matches(".*\\b(hi|hello|hey|what can you do|help me|how are you)\\b.*"))
            return Intent.GREET;

        // Compare
        if (q.contains("compare") || q.contains("vs") || q.contains("versus")
                || q.contains("difference between") || q.contains("which is better"))
            return Intent.COMPARE;

        // Price filter
        if (q.contains("under ₹") || q.contains("below ₹") || q.contains("less than ₹")
                || q.contains("under rs") || q.contains("below rs")
                || q.matches(".*under \\d+.*") || q.matches(".*below \\d+.*")
                || q.contains("budget") || q.contains("cheap") || q.contains("affordable"))
            return Intent.PRICE_FILTER;

        // Summary / review
        if (q.contains("review") || q.contains("summary") || q.contains("summarize")
                || q.contains("feedback") || q.contains("opinion") || q.contains("rating"))
            return Intent.SUMMARY;

        // Recommend
        if (q.contains("recommend") || q.contains("suggest") || q.contains("best")
                || q.contains("top") || q.contains("popular") || q.contains("trending"))
            return Intent.RECOMMEND;

        // Category browse
        if (q.contains("category") || q.contains("show all") || q.contains("list all")
                || q.contains("browse") || q.contains("what do you have"))
            return Intent.CATEGORY_BROWSE;

        // Order help
        if (q.contains("order") || q.contains("track") || q.contains("delivery")
                || q.contains("cancel") || q.contains("return") || q.contains("refund"))
            return Intent.ORDER_HELP;

        // Search
        if (q.contains("find") || q.contains("search") || q.contains("show") || q.contains("get")
                || q.contains("look for") || q.contains("i want") || q.contains("i need"))
            return Intent.SEARCH;

        return Intent.UNKNOWN;
    }
}
