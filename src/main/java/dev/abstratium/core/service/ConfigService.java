package dev.abstratium.core.service;

import dev.abstratium.core.entity.Config;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class ConfigService {

    @Inject
    EntityManager em;

    @ConfigProperty(name = "currency.code", defaultValue = "CHF")
    String defaultCurrencyCode;

    @ConfigProperty(name = "currency.locale", defaultValue = "en-US")
    String defaultLocale;

    public Optional<Config> find() {
        return em.createQuery("SELECT c FROM Config c", Config.class)
            .getResultStream()
            .findFirst();
    }

    @Transactional
    public Config getOrCreate() {
        Optional<Config> existing = find();
        if (existing.isPresent()) {
            return existing.get();
        }
        Config config = new Config();
        config.setId(UUID.randomUUID().toString());
        config.setCurrencyCode(defaultCurrencyCode);
        config.setLocale(defaultLocale);
        config.setCreatedAt(LocalDateTime.now());
        config.setUpdatedAt(LocalDateTime.now());
        em.persist(config);
        return config;
    }

    @Transactional
    public Config update(String currencyCode, String locale) {
        Config config = find().orElseThrow(() -> new IllegalStateException("Config not found for current tenant"));
        config.setCurrencyCode(currencyCode);
        config.setLocale(locale);
        config.setUpdatedAt(LocalDateTime.now());
        return config;
    }
}
