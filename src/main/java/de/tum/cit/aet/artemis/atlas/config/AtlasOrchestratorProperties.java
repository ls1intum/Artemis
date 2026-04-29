package de.tum.cit.aet.artemis.atlas.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Orchestrator properties consumed by {@link de.tum.cit.aet.artemis.atlas.service.CompetencyOrchestrationService}.
 * Strict binding catches typos under {@code artemis.atlas.orchestrator}; {@link #temperature()} is ignored
 * when {@link #reasoningEffort()} is non-blank because GPT-5 reasoning models reject explicit temperature.
 */
@ConfigurationProperties(prefix = "artemis.atlas.orchestrator", ignoreUnknownFields = false)
public record AtlasOrchestratorProperties(@DefaultValue("gpt-5.4") String model, @DefaultValue("1.0") double temperature, @DefaultValue("medium") String reasoningEffort) {
}
