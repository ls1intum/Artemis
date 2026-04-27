package de.tum.cit.aet.artemis.atlas.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration properties for the Atlas chat agent and competency orchestrator. Strict binding
 * (ignoreUnknownFields=false) fails fast at boot on typos in atlas-prefixed keys.
 * <p>
 * BREAKING (commit c5408b6c): {@code artemis.atlas.model} / {@code artemis.atlas.temperature} were
 * renamed to {@link #chatModel()} / {@link #chatTemperature()}; operators upgrading from 9.1.x
 * must update their overrides.
 * <p>
 * {@link #orchestratorTemperature()} is ignored when {@link #orchestratorReasoningEffort()} is set
 * because GPT-5 reasoning models reject non-default temperature; clear the reasoning effort to
 * opt back into the non-reasoning options build.
 */
@ConfigurationProperties(prefix = "artemis.atlas", ignoreUnknownFields = false)
public record AtlasOrchestratorProperties(@DefaultValue("gpt-5.4-mini") String chatModel, @DefaultValue("0.8") double chatTemperature,
        @DefaultValue("gpt-5.4") String orchestratorModel, @DefaultValue("1.0") double orchestratorTemperature, @DefaultValue("medium") String orchestratorReasoningEffort) {
}
