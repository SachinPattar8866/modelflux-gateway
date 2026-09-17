package com.modelflux.model.dto;

public class ChatResponse {

    private Long conversationId;
    private String response;
    private String providerUsed;

    public ChatResponse() {
    }

    public ChatResponse(Long conversationId, String response, String providerUsed) {
        this.conversationId = conversationId;
        this.response = response;
        this.providerUsed = providerUsed;
    }

    public Long getConversationId() {
        return conversationId;
    }

    public void setConversationId(Long conversationId) {
        this.conversationId = conversationId;
    }

    public String getResponse() {
        return response;
    }

    public void setResponse(String response) {
        this.response = response;
    }

    public String getProviderUsed() {
        return providerUsed;
    }

    public void setProviderUsed(String providerUsed) {
        this.providerUsed = providerUsed;
    }
}