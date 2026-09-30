package com.modelflux.controller;

import com.modelflux.model.dto.ChatMessage;
import com.modelflux.model.dto.ChatRequest;
import com.modelflux.model.entity.Conversation;
import com.modelflux.model.entity.Message;
import com.modelflux.provider.GroqProvider;
import com.modelflux.service.ConversationService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Flux;

import java.security.Principal;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Controller
public class ChatWebSocketController {

    private final ConversationService conversationService;
    private final GroqProvider groqProvider; // Phase 4: Groq only, direct — no orchestrator yet
    private final SimpMessagingTemplate messagingTemplate;

    public ChatWebSocketController(ConversationService conversationService,
                                   GroqProvider groqProvider,
                                   SimpMessagingTemplate messagingTemplate) {
        this.conversationService = conversationService;
        this.groqProvider = groqProvider;
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/chat")
    public void streamChat(ChatRequest request, Principal principal) {
        String userEmail = principal.getName();

        Conversation conversation = conversationService.getOrCreateConversation(
                request.getConversationId(), userEmail, request.getMessage());

        conversationService.saveMessage(conversation, Message.Role.USER, request.getMessage(), null);

        List<ChatMessage> history = conversationService.getHistoryForProvider(conversation.getId());

        // Accumulates the full reply as tokens stream in, so we can persist it once complete
        AtomicReference<StringBuilder> fullReply = new AtomicReference<>(new StringBuilder());

        Flux<String> tokenStream = groqProvider.streamMessage(history);

        tokenStream.subscribe(
                token -> {
                    fullReply.get().append(token);
                    messagingTemplate.convertAndSendToUser(userEmail, "/queue/chat",
                            new StreamChunk(conversation.getId(), token, false));
                },
                error -> {
                    messagingTemplate.convertAndSendToUser(userEmail, "/queue/chat",
                            new StreamChunk(conversation.getId(), "Error: " + error.getMessage(), true));
                },
                () -> {
                    // Stream completed successfully — persist the full assembled message
                    String finalReply = fullReply.get().toString();
                    conversationService.saveAssistantMessage(conversation.getId(), finalReply, "GROQ");
                    messagingTemplate.convertAndSendToUser(userEmail, "/queue/chat",
                            new StreamChunk(conversation.getId(), null, true));
                }
        );
    }

    public static class StreamChunk {
        public final Long conversationId;
        public final String token;
        public final boolean done;

        public StreamChunk(Long conversationId, String token, boolean done) {
            this.conversationId = conversationId;
            this.token = token;
            this.done = done;
        }
    }
}