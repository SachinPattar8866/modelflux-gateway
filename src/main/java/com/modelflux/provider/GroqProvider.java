package com.modelflux.provider;

import com.modelflux.model.dto.ChatMessage;
import com.modelflux.model.enums.ProviderName;
import com.modelflux.provider.ratelimit.GroqRateLimitParser;
import com.modelflux.provider.ratelimit.RateLimitHeaderParser.RateLimitInfo;
import com.modelflux.service.ProviderRateLimitService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
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

@Order(1)
@Service
public class GroqProvider implements AIProvider {

    private final WebClient webClient;
    private final String apiKey;
    private final String apiUrl;
    private final String model;
    private final GroqRateLimitParser rateLimitParser;
    private final ProviderRateLimitService rateLimitService;

    public GroqProvider(
            WebClient webClient,
            @Value("${groq.api.key}") String apiKey,
            @Value("${groq.api.url}") String apiUrl,
            @Value("${groq.model}") String model,
            GroqRateLimitParser rateLimitParser,
            ProviderRateLimitService rateLimitService) {
        this.webClient = webClient;
        this.apiKey = apiKey;
        this.apiUrl = apiUrl;
        this.model = model;
        this.rateLimitParser = rateLimitParser;
        this.rateLimitService = rateLimitService;
    }

    @Override
    public ProviderName getProviderName() {
        return ProviderName.GROQ;
    }

    @Override
    public String sendMessage(List<ChatMessage> chatHistory) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);

        List<Map<String, String>> messages = new ArrayList<>();
        for (ChatMessage msg : chatHistory) {
            Map<String, String> messageMap = new HashMap<>();
            messageMap.put("role", msg.getRole());
            messageMap.put("content", msg.getContent());
            messages.add(messageMap);
        }
        requestBody.put("messages", messages);

        ResponseEntity<GroqResponse> response;
        try {
            response = webClient.post()
                    .uri(apiUrl)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .toEntity(GroqResponse.class)
                    .block();
        } catch (WebClientResponseException e) {
            RateLimitInfo info = rateLimitParser.parse(e.getHeaders());
            rateLimitService.recordRateLimitInfo(ProviderName.GROQ, info);
            throw new com.modelflux.exception.ProviderApiException(
                    "Groq API returned an error: " + e.getStatusCode() + " - " + e.getResponseBodyAsString(),
                    e.getStatusCode(), e);
        } catch (WebClientRequestException e) {
            throw e; // let Resilience4j's retry predicate handle this directly — it already knows WebClientRequestException is retryable
        }

        RateLimitInfo info = rateLimitParser.parse(response.getHeaders());
        rateLimitService.recordRateLimitInfo(ProviderName.GROQ, info);

        GroqResponse body = response.getBody();
        if (body != null && body.choices != null && !body.choices.isEmpty()) {
            return body.choices.get(0).message.content;
        }

        throw new RuntimeException("Received an empty or malformed response from Groq API");
    }

    private static class GroqResponse {
        public List<Choice> choices;
    }

    private static class Choice {
        public GroqMessage message;
    }

    private static class GroqMessage {
        public String content;
    }

    @Override
    public reactor.core.publisher.Flux<String> streamMessage(List<ChatMessage> chatHistory) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("stream", true);

        List<Map<String, String>> messages = new ArrayList<>();
        for (ChatMessage msg : chatHistory) {
            Map<String, String> messageMap = new HashMap<>();
            messageMap.put("role", msg.getRole());
            messageMap.put("content", msg.getContent());
            messages.add(messageMap);
        }
        requestBody.put("messages", messages);

        return webClient.post()
                .uri(apiUrl)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToFlux(String.class)
                .filter(chunk -> !chunk.isBlank() && !chunk.trim().equals("[DONE]"))
                .mapNotNull(this::extractTokenFromChunk);
    }

    private String extractTokenFromChunk(String chunk) {
        try {
            String json = chunk.startsWith("data:") ? chunk.substring(5).trim() : chunk.trim();
            if (json.isEmpty() || json.equals("[DONE]")) {
                return null;
            }
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(json);
            com.fasterxml.jackson.databind.JsonNode delta = node.path("choices").path(0).path("delta").path("content");
            return delta.isMissingNode() ? null : delta.asText();
        } catch (Exception e) {
            return null; // malformed or partial chunk — skip it rather than break the stream
        }
    }
}