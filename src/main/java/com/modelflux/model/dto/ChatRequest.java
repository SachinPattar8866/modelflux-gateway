package com.modelflux.model.dto;

public class ChatRequest {

    private Long conversationId; // Nullable; null indicates starting a new conversation
    private String message;
    private String preferredProvider; // Optional; handles the UI dropdown override later

    public ChatRequest() {
    }

    public ChatRequest(Long conversationId, String message, String preferredProvider) {
        this.conversationId = conversationId;
        this.message = message;
        this.preferredProvider = preferredProvider;
    }

    public Long getConversationId() {
        return conversationId;
    }

    public void setConversationId(Long conversationId) {
        this.conversationId = conversationId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getPreferredProvider() {
        return preferredProvider;
    }

    public void setPreferredProvider(String preferredProvider) {
        this.preferredProvider = preferredProvider;
    }
}