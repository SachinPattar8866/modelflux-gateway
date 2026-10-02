package com.modelflux.controller;

import com.modelflux.model.dto.ChatMessage;
import com.modelflux.model.dto.ChatRequest;
import com.modelflux.model.entity.Conversation;
import com.modelflux.model.entity.Message;
import com.modelflux.model.enums.ProviderName;
import com.modelflux.provider.AIProvider;
import com.modelflux.provider.ProviderFactory;
import com.modelflux.service.ConversationService;
import com.modelflux.service.ProviderRateLimitService;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Controller
public class ChatWebSocketController {

    private static final Logger log = LoggerFactory.getLogger(ChatWebSocketController.class);

    private final ConversationService conversationService;
    private final ProviderFactory providerFactory;
    private final ProviderRateLimitService rateLimitService;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final SimpMessagingTemplate messagingTemplate;

    public ChatWebSocketController(ConversationService conversationService,
                                   ProviderFactory providerFactory,
                                   ProviderRateLimitService rateLimitService,
                                   CircuitBreakerRegistry circuitBreakerRegistry,
                                   SimpMessagingTemplate messagingTemplate) {
        this.conversationService = conversationService;
        this.providerFactory = providerFactory;
        this.rateLimitService = rateLimitService;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/chat")
    public void streamChat(ChatRequest request, Principal principal) {
        String userEmail = principal.getName();

        Conversation conversation = conversationService.getOrCreateConversation(
                request.getConversationId(), userEmail, request.getMessage());

        conversationService.saveMessage(conversation, Message.Role.USER, request.getMessage(), null);

        List<ChatMessage> history = conversationService.getHistoryForProvider(conversation.getId());
        List<AIProvider> candidates = buildProviderOrder(request.getPreferredProvider());

        attemptStream(candidates, 0, history, conversation, userEmail);
    }

    private void attemptStream(List<AIProvider> candidates, int index,
                               List<ChatMessage> history, Conversation conversation, String userEmail) {

        if (index >= candidates.size()) {
            messagingTemplate.convertAndSendToUser(userEmail, "/queue/chat",
                    new StreamChunk(conversation.getId(), "Error: all providers failed", true, false));
            return;
        }

        AIProvider provider = candidates.get(index);
        CircuitBreaker breaker = circuitBreakerRegistry.circuitBreaker(provider.getProviderName().name());

        if (rateLimitService.isBlocked(provider.getProviderName()) || breaker.getState() == CircuitBreaker.State.OPEN) {
            log.info("Skipping {} — rate-limited or circuit OPEN", provider.getProviderName());
            attemptStream(candidates, index + 1, history, conversation, userEmail);
            return;
        }

        StringBuilder attemptBuffer = new StringBuilder();
        boolean[] anyTokenSent = { false };
        long startNanos = System.nanoTime();

        provider.streamMessage(history).subscribe(
                token -> {
                    anyTokenSent[0] = true;
                    attemptBuffer.append(token);
                    messagingTemplate.convertAndSendToUser(userEmail, "/queue/chat",
                            new StreamChunk(conversation.getId(), token, false, false));
                },
                error -> {
                    long elapsedNanos = System.nanoTime() - startNanos;
                    // Manually record the failure against the circuit breaker — this is the piece
                    // that was missing. Without it, a provider that dies mid-stream (after headers
                    // already succeeded) never gets flagged as unhealthy, since no new headers
                    // arrive to trigger recordRateLimitInfo().
                    breaker.onError(elapsedNanos, TimeUnit.NANOSECONDS, error);

                    log.warn("Provider {} failed mid-stream: {}", provider.getProviderName(), error.getMessage());
                    if (anyTokenSent[0]) {
                        messagingTemplate.convertAndSendToUser(userEmail, "/queue/chat",
                                new StreamChunk(conversation.getId(),
                                        "Switching from " + provider.getProviderName() + "...", false, true));
                    }
                    attemptStream(candidates, index + 1, history, conversation, userEmail);
                },
                () -> {
                    long elapsedNanos = System.nanoTime() - startNanos;
                    breaker.onSuccess(elapsedNanos, TimeUnit.NANOSECONDS);

                    String finalReply = attemptBuffer.toString();
                    conversationService.saveAssistantMessage(conversation.getId(), finalReply, provider.getProviderName().name());
                    messagingTemplate.convertAndSendToUser(userEmail, "/queue/chat",
                            new StreamChunk(conversation.getId(), null, true, false));
                }
        );
    }

    private List<AIProvider> buildProviderOrder(String preferredProviderName) {
        List<AIProvider> defaultOrder = providerFactory.getProvidersInFallbackOrder();

        if (preferredProviderName == null || preferredProviderName.isBlank()
                || "AUTO".equalsIgnoreCase(preferredProviderName)) {
            return defaultOrder;
        }

        try {
            ProviderName name = ProviderName.valueOf(preferredProviderName.toUpperCase());
            Optional<AIProvider> preferred = providerFactory.getByName(name);
            if (preferred.isPresent()) {
                List<AIProvider> ordered = new ArrayList<>();
                ordered.add(preferred.get());
                for (AIProvider p : defaultOrder) {
                    if (p.getProviderName() != name) ordered.add(p);
                }
                return ordered;
            }
        } catch (IllegalArgumentException ignored) {
        }

        return defaultOrder;
    }

    public static class StreamChunk {
        public final Long conversationId;
        public final String token;
        public final boolean done;
        public final boolean switching;

        public StreamChunk(Long conversationId, String token, boolean done, boolean switching) {
            this.conversationId = conversationId;
            this.token = token;
            this.done = done;
            this.switching = switching;
        }
    }
}