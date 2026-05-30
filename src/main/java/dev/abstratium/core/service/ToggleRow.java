package dev.abstratium.core.service;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;

@RegisterForReflection
public record ToggleRow(String toggleName, String toggleDescription, boolean toggleEnabled, String stageName, String ruleName, int priority, String value, List<RuleCriterion> ruleCriteria) {
}
