package com.modelflux.provider;

import com.modelflux.model.enums.ProviderName;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class ProviderFactory {

    // Fallback order is enforced by @Order annotations on each AIProvider
    // implementation (Groq=1, Gemini=2, OpenRouter=3) — Spring sorts the
    // injected list accordingly, so this class doesn't need to sort manually.
    private final List<AIProvider> providers;

    public ProviderFactory(List<AIProvider> providers) {
        this.providers = providers;
    }

    public List<AIProvider> getProvidersInFallbackOrder() {
        return providers;
    }

    public Optional<AIProvider> getByName(ProviderName name) {
        return providers.stream()
                .filter(p -> p.getProviderName() == name)
                .findFirst();
    }
}