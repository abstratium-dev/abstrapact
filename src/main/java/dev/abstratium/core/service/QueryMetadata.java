package dev.abstratium.core.service;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record QueryMetadata(int count, boolean cacheHit) {
}
