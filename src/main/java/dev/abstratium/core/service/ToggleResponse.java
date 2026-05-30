package dev.abstratium.core.service;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;

@RegisterForReflection
public record ToggleResponse(List<ToggleRow> toggles, QueryMetadata queryMetadata) {
}
