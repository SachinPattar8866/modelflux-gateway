package com.modelflux.provider;

import com.modelflux.model.dto.ChatMessage;
import com.modelflux.model.enums.ProviderName;
import com.modelflux.provider.ratelimit.OpenRouterRateLimitParser;
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

@Order(3)
@Service
public class OpenRouterProvider implements AIProvider {

    private final WebClient webClient;
    private final String apiKey;
    private final String apiUrl;
    private final String model;
    private final OpenRouterRateLimitParser rateLimitParser;
    private final ProviderRateLimitService rateLimitService;

    public OpenRouterProvider(
            WebClient webClient,
            @Value("${openrouter.api.key}") String apiKey,
            @Value("${openrouter.api.url}") String apiUrl,
            @Value("${openrouter.model}") String model,
            OpenRouterRateLimitParser rateLimitParser,
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
        return ProviderName.OPENROUTER;
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

        ResponseEntity<OpenRouterResponse> response;
        try {
            response = webClient.post()
                    .uri(apiUrl)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .toEntity(OpenRouterResponse.class)
                    .block();
        } catch (WebClientResponseException e) {
            RateLimitInfo info = rateLimitParser.parse(e.getHeaders());
            rateLimitService.recordRateLimitInfo(ProviderName.OPENROUTER, info);
            throw new com.modelflux.exception.ProviderApiException(
                    "OpenRouter API returned an error: " + e.getStatusCode() + " - " + e.getResponseBodyAsString(), e.getStatusCode(), e);
        } catch (WebClientRequestException e) {
            throw e;
        }

        RateLimitInfo info = rateLimitParser.parse(response.getHeaders());
        rateLimitService.recordRateLimitInfo(ProviderName.OPENROUTER, info);

        OpenRouterResponse body = response.getBody();
        if (body != null && body.choices != null && !body.choices.isEmpty()) {
            return body.choices.get(0).message.content;
        }

        throw new RuntimeException("Received an empty or malformed response from OpenRouter API");
    }

    private static class OpenRouterResponse {
        public List<Choice> choices;
    }

    private static class Choice {
        public OpenRouterMessage message;
    }

    private static class OpenRouterMessage {
        public String content;
    }
}