package dev.abstratium.core.service;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record RuleCriterion(String criterionKey, String criterionValue) {
}
