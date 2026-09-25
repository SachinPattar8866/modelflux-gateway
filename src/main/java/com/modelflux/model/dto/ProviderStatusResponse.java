package com.modelflux.model.dto;

import com.modelflux.model.enums.ProviderName;
import com.modelflux.model.enums.ProviderStatus;

public class ProviderStatusResponse {

    private ProviderName provider;
    private ProviderStatus status;
    private Long resetInSeconds; // null when not applicable (e.g. status is ACTIVE)

    public ProviderStatusResponse() {
    }

    public ProviderStatusResponse(ProviderName provider, ProviderStatus status, Long resetInSeconds) {
        this.provider = provider;
        this.status = status;
        this.resetInSeconds = resetInSeconds;
    }

    public ProviderName getProvider() { return provider; }
    public void setProvider(ProviderName provider) { this.provider = provider; }

    public ProviderStatus getStatus() { return status; }
    public void setStatus(ProviderStatus status) { this.status = status; }

    public Long getResetInSeconds() { return resetInSeconds; }
    public void setResetInSeconds(Long resetInSeconds) { this.resetInSeconds = resetInSeconds; }
}