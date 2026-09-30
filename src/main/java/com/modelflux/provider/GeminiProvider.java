package com.modelflux.provider;

import com.modelflux.model.dto.ChatMessage;
import com.modelflux.model.enums.ProviderName;
import com.modelflux.provider.ratelimit.GeminiRateLimitParser;
import com.modelflux.provider.ratelimit.RateLimitHeaderParser.RateLimitInfo;
import com.modelflux.service.ProviderRateLimitService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.core.annotation.Order;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Order(2)
@Service
public class GeminiProvider implements AIProvider {

    private final WebClient webClient;
    private final String apiKey;
    private final String apiUrl;
    private final String model;
    private final GeminiRateLimitParser rateLimitParser;
    private final ProviderRateLimitService rateLimitService;

    public GeminiProvider(
            WebClient webClient,
            @Value("${gemini.api.key}") String apiKey,
            @Value("${gemini.api.url}") String apiUrl,
            @Value("${gemini.model}") String model,
            GeminiRateLimitParser rateLimitParser,
            ProviderRateLimitService rateLimitService) {
        this.webClient = webClient;
        this.apiKey = apiKey;
        this.apiUrl = apiUrl;
        this.model = model;
        this.rateLimitParser = rateLimitParser;
        this.rateLimitService = rateLimitService;
    }

    @Override
    public reactor.core.publisher.Flux<String> streamMessage(List<ChatMessage> chatHistory) {
        throw new UnsupportedOperationException("Streaming not yet implemented for Gemini");
    }

    @Override
    public ProviderName getProviderName() {
        return ProviderName.GEMINI;
    }

    @Override
    public String sendMessage(List<ChatMessage> chatHistory) {
        List<Map<String, Object>> contents = new ArrayList<>();
        for (ChatMessage msg : chatHistory) {
            Map<String, Object> entry = new HashMap<>();
            String geminiRole = "assistant".equals(msg.getRole()) ? "model" : "user";
            entry.put("role", geminiRole);
            entry.put("parts", List.of(Map.of("text", msg.getContent())));
            contents.add(entry);
        }

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("contents", contents);

        String fullUrl = apiUrl + model + ":generateContent?key=" + apiKey;

        ResponseEntity<GeminiResponse> response;
        try {
            response = webClient.post()
                    .uri(fullUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .toEntity(GeminiResponse.class)
                    .block();
        } catch (WebClientResponseException e) {
            RateLimitInfo info = rateLimitParser.parse(e.getHeaders());
            rateLimitService.recordRateLimitInfo(ProviderName.GEMINI, info);
            throw new com.modelflux.exception.ProviderApiException(
                    "Gemini API returned an error: " + e.getStatusCode() + " - " + e.getResponseBodyAsString(), e.getStatusCode(), e);
        } catch (WebClientRequestException e) {
            throw e;
        }

        RateLimitInfo info = rateLimitParser.parse(response.getHeaders());
        rateLimitService.recordRateLimitInfo(ProviderName.GEMINI, info);

        GeminiResponse body = response.getBody();
        if (body != null && body.candidates != null && !body.candidates.isEmpty()) {
            Candidate first = body.candidates.get(0);
            if (first.content != null && first.content.parts != null && !first.content.parts.isEmpty()) {
                return first.content.parts.get(0).text;
            }
        }

        throw new RuntimeException("Received an empty or malformed response from Gemini API");
    }

    private static class GeminiResponse {
        public List<Candidate> candidates;
    }

    private static class Candidate {
        public Content content;
    }

    private static class Content {
        public List<Part> parts;
    }

    private static class Part {
        public String text;
    }
}