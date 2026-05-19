package de.tum.cit.aet.artemis.atlas.config;

import jakarta.validation.constraints.Positive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Orchestrator properties consumed by {@link de.tum.cit.aet.artemis.atlas.service.CompetencyOrchestrationService}
 * and the auto-orchestration pipeline ({@link de.tum.cit.aet.artemis.atlas.service.ContentChangeAccumulatorService},
 * {@link de.tum.cit.aet.artemis.atlas.service.ContentChangeScheduler}). Strict binding catches typos under
 * {@code artemis.atlas.orchestrator}; {@link #temperature()} is ignored when {@link #reasoningEffort()} is
 * non-blank because GPT-5 reasoning models reject explicit temperature.
 *
 * @param model                  Azure deployment alias for the orchestrator chat model.
 * @param temperature            Sampling temperature; ignored when {@link #reasoningEffort()} is non-blank.
 * @param reasoningEffort        Reasoning effort for GPT-5 family models; blank disables reasoning options.
 * @param debounceWindowSeconds  Seconds without a new content change before a course's accumulator is eligible to fire.
 * @param maxDailyOrchestrations Per-course daily cap on auto-orchestration runs.
 * @param schedulerRateMs        Scheduler tick interval in milliseconds. Also used as the initial delay.
 */
@Validated
@ConfigurationProperties(prefix = "artemis.atlas.orchestrator", ignoreUnknownFields = false)
public record AtlasOrchestratorProperties(@DefaultValue("gpt-5.4") String model, @DefaultValue("1.0") double temperature, @DefaultValue("medium") String reasoningEffort,
        @DefaultValue("300") @Positive int debounceWindowSeconds, @DefaultValue("10") @Positive int maxDailyOrchestrations, @DefaultValue("30000") @Positive long schedulerRateMs) {
}
