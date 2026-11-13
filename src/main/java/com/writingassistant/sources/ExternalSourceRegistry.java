package com.writingassistant.sources;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe registry managing available ExternalSourceProvider implementations.
 */
public class ExternalSourceRegistry {

    private final Map<String, ExternalSourceProvider> providers = new ConcurrentHashMap<>();

    public ExternalSourceRegistry() { }

    public ExternalSourceRegistry(List<ExternalSourceProvider> initialProviders) {
        if (initialProviders != null) {
            for (ExternalSourceProvider provider : initialProviders) {
                register(provider);
            }
        }
    }

    public void register(ExternalSourceProvider provider) {
        Objects.requireNonNull(provider, "provider must not be null");
        providers.put(provider.getProviderId().toLowerCase(Locale.ROOT), provider);
    }

    public void unregister(String providerId) {
        if (providerId != null) {
            providers.remove(providerId.toLowerCase(Locale.ROOT));
        }
    }

    public Optional<ExternalSourceProvider> getProvider(String providerId) {
        if (providerId == null) return Optional.empty();
        return Optional.ofNullable(providers.get(providerId.toLowerCase(Locale.ROOT)));
    }

    public List<ExternalSourceProvider> getAllProviders() {
        return List.copyOf(providers.values());
    }

    public List<ExternalSourceProvider> getAvailableProviders() {
        return providers.values().stream()
                .filter(ExternalSourceProvider::isAvailable)
                .toList();
    }
}
