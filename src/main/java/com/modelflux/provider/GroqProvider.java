package com.modelflux.provider;

import com.modelflux.model.dto.ChatMessage;
import com.modelflux.model.enums.ProviderName;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class GroqProvider implements AIProvider {

    private final WebClient webClient;
    private final String apiKey;
    private final String apiUrl;
    private final String model;

    public GroqProvider(
            WebClient webClient,
            @Value("${groq.api.key}") String apiKey,
            @Value("${groq.api.url:https://api.groq.com/openai/v1/chat/completions}") String apiUrl,
            @Value("${groq.model:llama-3.3-70b-versatile}") String model) {
        this.webClient = webClient;
        this.apiKey = apiKey;
        this.apiUrl = apiUrl;
        this.model = model;
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

        GroqResponse response;
        try {
            response = webClient.post()
                    .uri(apiUrl)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(GroqResponse.class)
                    .block();
        } catch (WebClientResponseException e) {
            // Groq responded, but with an error status (4xx/5xx) — e.g. bad API key, rate limit, invalid request
            throw new RuntimeException(
                    "Groq API returned an error: " + e.getStatusCode() + " - " + e.getResponseBodyAsString(), e);
        } catch (WebClientRequestException e) {
            // Couldn't reach Groq at all — network issue, DNS failure, timeout, etc.
            throw new RuntimeException("Failed to reach Groq API: " + e.getMessage(), e);
        }

        if (response != null && response.choices != null && !response.choices.isEmpty()) {
            return response.choices.get(0).message.content;
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
}