package com.modelflux.controller;

import com.modelflux.model.dto.ChatMessage;
import com.modelflux.model.dto.ChatRequest;
import com.modelflux.model.dto.ChatResponse;
import com.modelflux.model.entity.Conversation;
import com.modelflux.model.entity.Message;
import com.modelflux.provider.AIProvider;
import com.modelflux.service.ConversationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ConversationService conversationService;
    private final AIProvider aiProvider; // Phase 2: directly wired to GroqProvider (single bean of this type)

    public ChatController(ConversationService conversationService, AIProvider aiProvider) {
        this.conversationService = conversationService;
        this.aiProvider = aiProvider;
    }

    @PostMapping
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest request, Authentication authentication) {
        String userEmail = authentication.getName();

        // 1. Get or create the conversation
        Conversation conversation = conversationService.getOrCreateConversation(
                request.getConversationId(), userEmail, request.getMessage());

        // 2. Save the user's message
        conversationService.saveMessage(conversation, Message.Role.USER, request.getMessage(), null);

        // 3. Build history (including the just-saved user message) and call the provider
        List<ChatMessage> history = conversationService.getHistoryForProvider(conversation.getId());
        String aiReply = aiProvider.sendMessage(history);

        // 4. Save the assistant's reply, tagged with which provider answered
        conversationService.saveMessage(
                conversation, Message.Role.ASSISTANT, aiReply, aiProvider.getProviderName().name());

        // 5. Return the response
        return ResponseEntity.ok(new ChatResponse(conversation.getId(), aiReply, aiProvider.getProviderName().name()));
    }
}