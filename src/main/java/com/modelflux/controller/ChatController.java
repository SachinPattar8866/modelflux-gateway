package com.modelflux.controller;

import com.modelflux.model.dto.ChatMessage;
import com.modelflux.model.dto.ChatRequest;
import com.modelflux.model.dto.ChatResponse;
import com.modelflux.model.entity.Conversation;
import com.modelflux.model.entity.Message;
import com.modelflux.service.ChatOrchestratorService;
import com.modelflux.service.ChatOrchestratorService.ChatResult;
import com.modelflux.service.ConversationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.modelflux.model.dto.ConversationSummaryResponse;
import com.modelflux.model.dto.MessageResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import java.util.List;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ConversationService conversationService;
    private final ChatOrchestratorService chatOrchestratorService;

    public ChatController(ConversationService conversationService, ChatOrchestratorService chatOrchestratorService) {
        this.conversationService = conversationService;
        this.chatOrchestratorService = chatOrchestratorService;
    }

    @GetMapping
    public ResponseEntity<List<ConversationSummaryResponse>> listConversations(Authentication authentication) {
        String userEmail = authentication.getName();
        List<ConversationSummaryResponse> conversations = conversationService.listConversationsForUser(userEmail);
        return ResponseEntity.ok(conversations);
    }

    @GetMapping("/{conversationId}/messages")
    public ResponseEntity<List<MessageResponse>> getMessages(@PathVariable Long conversationId, Authentication authentication) {
        String userEmail = authentication.getName();
        List<MessageResponse> messages = conversationService.getMessagesForUser(conversationId, userEmail);
        return ResponseEntity.ok(messages);
    }

    @PostMapping
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest request, Authentication authentication) {
        String userEmail = authentication.getName();

        Conversation conversation = conversationService.getOrCreateConversation(
                request.getConversationId(), userEmail, request.getMessage());

        conversationService.saveMessage(conversation, Message.Role.USER, request.getMessage(), null);

        List<ChatMessage> history = conversationService.getHistoryForProvider(conversation.getId());

        ChatResult result = chatOrchestratorService.sendMessage(history, request.getPreferredProvider());

        conversationService.saveMessage(
                conversation, Message.Role.ASSISTANT, result.reply, result.providerUsed.name());

        return ResponseEntity.ok(new ChatResponse(conversation.getId(), result.reply, result.providerUsed.name()));
    }
}