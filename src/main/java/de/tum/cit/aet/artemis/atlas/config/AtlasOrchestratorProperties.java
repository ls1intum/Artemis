package de.tum.cit.aet.artemis.atlas.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration properties for the Atlas chat agent and the Atlas competency orchestrator.
 * <p>
 * Replaces the previous three independent {@link org.springframework.beans.factory.annotation.Value}
 * injections in {@link de.tum.cit.aet.artemis.atlas.service.CompetencyOrchestrationService} and
 * {@link de.tum.cit.aet.artemis.atlas.service.AtlasAgentDelegationService}: a typo in any of the
 * old keys silently fell back to the default. Strict {@link ConfigurationProperties} binding
 * fails fast at boot when an unknown atlas-prefixed key is set.
 * <p>
 * BREAKING (commit c5408b6c): the previous {@code artemis.atlas.model} and
 * {@code artemis.atlas.temperature} keys were renamed to {@link #chatModel()} and
 * {@link #chatTemperature()}. Operators upgrading from 9.1.x must update their overrides.
 *
 * @param chatModel                   Azure deployment alias for the conversational Atlas agent
 *                                        (used by {@link de.tum.cit.aet.artemis.atlas.service.AtlasAgentDelegationService}).
 * @param chatTemperature             sampling temperature for the chat agent
 * @param orchestratorModel           Azure deployment alias for the competency orchestrator (used
 *                                        by {@link de.tum.cit.aet.artemis.atlas.service.CompetencyOrchestrationService}).
 * @param orchestratorTemperature     sampling temperature for the orchestrator; ignored when
 *                                        {@link #orchestratorReasoningEffort()} is set, because
 *                                        GPT-5 reasoning models reject any non-default temperature.
 * @param orchestratorReasoningEffort reasoning effort passed to Azure OpenAI's reasoning models;
 *                                        leave blank to fall back to a regular non-reasoning chat
 *                                        options build.
 */
@ConfigurationProperties(prefix = "artemis.atlas")
public record AtlasOrchestratorProperties(@DefaultValue("gpt-5.4-mini") String chatModel, @DefaultValue("0.8") double chatTemperature,
        @DefaultValue("gpt-5.4") String orchestratorModel, @DefaultValue("1.0") double orchestratorTemperature, @DefaultValue("medium") String orchestratorReasoningEffort) {
}
