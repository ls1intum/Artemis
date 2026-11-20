package de.tum.cit.aet.artemis.atlas.config;

import jakarta.validation.constraints.Positive;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Settings of autonomous competency orchestration. The models it uses are assigned under {@code artemis.atlas.atlasllm.llm-presets}.
 *
 * @param model                  no longer read; accepted for one release so an old configuration only produces a warning
 * @param temperature            no longer read; see {@code model}
 * @param reasoningEffort        no longer read; see {@code model}
 * @param workerModel            no longer read; see {@code model}
 * @param workerReasoningEffort  no longer read; see {@code model}
 * @param responsesApiEnabled    no longer read; see {@code model}
 * @param debounceWindowSeconds  quiet period after the last content edit before a course is orchestrated
 * @param maxDailyOrchestrations maximum automatic orchestrations per course and day
 * @param schedulerRateMs        interval of the orchestration scheduler
 */
@Validated
@ConfigurationProperties(prefix = "artemis.atlas.orchestrator", ignoreUnknownFields = false)
public record AtlasOrchestratorProperties(@Deprecated(forRemoval = true) @Nullable String model, @Deprecated(forRemoval = true) @Nullable Double temperature,
        @Deprecated(forRemoval = true) @Nullable String reasoningEffort, @Deprecated(forRemoval = true) @Nullable String workerModel,
        @Deprecated(forRemoval = true) @Nullable String workerReasoningEffort, @Deprecated(forRemoval = true) @Nullable Boolean responsesApiEnabled,
        @DefaultValue("1800") @Positive int debounceWindowSeconds, @DefaultValue("10") @Positive int maxDailyOrchestrations,
        @DefaultValue("30000") @Positive long schedulerRateMs) {
}
