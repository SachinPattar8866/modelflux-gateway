package com.modelflux.model.dto;

import java.time.LocalDateTime;

public class MessageResponse {
    private String role;
    private String content;
    private String providerUsed;
    private LocalDateTime createdAt;

    public MessageResponse(String role, String content, String providerUsed, LocalDateTime createdAt) {
        this.role = role;
        this.content = content;
        this.providerUsed = providerUsed;
        this.createdAt = createdAt;
    }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getProviderUsed() { return providerUsed; }
    public void setProviderUsed(String providerUsed) { this.providerUsed = providerUsed; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}