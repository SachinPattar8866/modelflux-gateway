package com.modelflux.provider;

import com.modelflux.model.dto.ChatMessage;
import com.modelflux.model.enums.ProviderName;
import reactor.core.publisher.Flux;

import java.util.List;

public interface AIProvider {

    ProviderName getProviderName();

    String sendMessage(List<ChatMessage> chatHistory);

    /**
     * Streams the AI's response token-by-token. Phase 4: implemented for Groq only.
     * Other providers can throw UnsupportedOperationException until their own
     * streaming support is added.
     */
    Flux<String> streamMessage(List<ChatMessage> chatHistory);
}