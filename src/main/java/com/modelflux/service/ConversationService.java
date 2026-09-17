package com.modelflux.service;

import com.modelflux.model.dto.ChatMessage;
import com.modelflux.model.entity.Conversation;
import com.modelflux.model.entity.Message;
import com.modelflux.model.entity.User;
import com.modelflux.repository.ConversationRepository;
import com.modelflux.repository.MessageRepository;
import com.modelflux.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ConversationService {

    private static final int MAX_HISTORY_MESSAGES = 20; // simple cap for Phase 2, per our earlier plan

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;

    public ConversationService(ConversationRepository conversationRepository,
                               MessageRepository messageRepository,
                               UserRepository userRepository) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public Conversation getOrCreateConversation(Long conversationId, String userEmail, String firstMessage) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userEmail));

        if (conversationId != null) {
            return conversationRepository.findByIdAndUserId(conversationId, user.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Conversation not found or access denied"));
        }

        // New conversation — title derived from the first message (truncated)
        Conversation conversation = new Conversation();
        conversation.setUser(user);
        conversation.setTitle(deriveTitle(firstMessage));
        return conversationRepository.save(conversation);
    }

    @Transactional
    public Message saveMessage(Conversation conversation, Message.Role role, String content, String providerUsed) {
        Message message = new Message();
        message.setConversation(conversation);
        message.setRole(role);
        message.setContent(content);
        message.setProviderUsed(providerUsed); // null for USER role, set for ASSISTANT

        conversation.setUpdatedAt(java.time.LocalDateTime.now());
        conversationRepository.save(conversation);

        return messageRepository.save(message);
    }

    @Transactional(readOnly = true)
    public List<ChatMessage> getHistoryForProvider(Long conversationId) {
        List<Message> messages = messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);

        // Cap to the most recent N messages to stay within context limits
        int fromIndex = Math.max(0, messages.size() - MAX_HISTORY_MESSAGES);
        List<Message> trimmed = messages.subList(fromIndex, messages.size());

        return trimmed.stream()
                .map(m -> new ChatMessage(m.getRole().name().toLowerCase(), m.getContent()))
                .collect(Collectors.toList());
    }

    private String deriveTitle(String firstMessage) {
        if (firstMessage == null || firstMessage.isBlank()) {
            return "New Conversation";
        }
        String trimmed = firstMessage.trim();
        return trimmed.length() > 50 ? trimmed.substring(0, 50) + "..." : trimmed;
    }
}