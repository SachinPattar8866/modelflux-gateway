package com.modelflux.provider;

import com.modelflux.model.dto.ChatMessage;
import com.modelflux.model.enums.ProviderName;

import java.util.List;

public interface AIProvider {

    /**
     * Identifies which provider this implementation represents.
     * Crucial for the orchestrator to map failures and routing choices (Phase 3).
     */
    ProviderName getProviderName();

    /**
     * Standard synchronous call for Phase 2.
     * Takes the conversation history as plain DTOs (not JPA entities) and
     * returns the AI's generated response string.
     */
    String sendMessage(List<ChatMessage> chatHistory);

    /**
     * Placeholder for Phase 4 (WebSockets/SSE streaming).
     * Will be implemented later using Project Reactor's Flux.
     *
     * default Flux<String> streamMessage(List<ChatMessage> chatHistory) {
     *     throw new UnsupportedOperationException("Streaming not yet implemented");
     * }
     */
}