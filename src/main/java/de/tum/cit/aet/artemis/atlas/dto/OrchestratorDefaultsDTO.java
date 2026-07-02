package de.tum.cit.aet.artemis.atlas.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Global default values for the per-course Atlas auto-orchestration overrides, surfaced to the
 * course-settings form so it can show the instructor what an empty (unset) override resolves to.
 * Sourced from {@code AtlasOrchestratorProperties} (server-side YAML); the per-course overrides on
 * {@link de.tum.cit.aet.artemis.atlas.domain.competency.CourseAutoOrchestrationConfiguration} fall
 * back to these defaults when not set.
 *
 * @param debounceWindowSeconds  global default debounce window in seconds
 * @param maxDailyOrchestrations global default daily run cap
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record OrchestratorDefaultsDTO(int debounceWindowSeconds, int maxDailyOrchestrations) {
}
