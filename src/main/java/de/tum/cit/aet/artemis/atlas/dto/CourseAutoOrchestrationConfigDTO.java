package de.tum.cit.aet.artemis.atlas.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Lightweight projection of a course's Atlas auto-orchestration configuration. Read on the
 * accumulator hot path ({@code record} / {@code claimDueBatch} / {@code listDueCourseIds}) so the
 * pipeline can resolve the per-course kill switch and the debounce / daily-cap overrides without
 * loading the full configuration entity. The config stays authoritative in the database and is
 * never duplicated into the distributed accumulator state.
 *
 * @param autoOrchestratorEnabled       hard per-course kill switch for the auto-orchestration pipeline
 * @param debounceWindowSecondsOverride per-course debounce window override in seconds, or {@code null} to use the global default
 * @param maxDailyOrchestrationOverride per-course daily run cap override, or {@code null} to use the global default
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseAutoOrchestrationConfigDTO(boolean autoOrchestratorEnabled, @Nullable Integer debounceWindowSecondsOverride, @Nullable Integer maxDailyOrchestrationOverride) {
}
