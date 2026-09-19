package de.tum.cit.aet.artemis.iris.service.pyris.dto.search;

import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.iris.service.pyris.dto.PyrisPipelineExecutionSettingsDTO;

/**
 * Request body sent to Pyris POST /api/v1/pipelines/global-search/run (async, returns 202).
 * Pyris sends two webhook callbacks back to Artemis:
 * 1. A "thinking" callback when it decides the query requires an LLM answer.
 * 2. A "result" callback when the pipeline finishes, with the final answer (or null).
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisGlobalSearchAnswerRequestDTO(@NotBlank String query, @Min(1) @Max(5) int limit, PyrisPipelineExecutionSettingsDTO settings,
        @Nullable PyrisAccessContextDTO accessContext, @Nullable List<PyrisEntityCandidateDTO> entityCandidates,
        // NON_NULL, not the record-level NON_EMPTY: an EMPTY list here means the caller already resolved the course
        // scope to nothing (e.g. every requested course was excluded) and must reach Pyris as an empty list, not
        // collapse into "absent" (which Pyris reads as unscoped — the opposite of what an all-excluded scope means).
        @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable List<Long> courseIds) {
}
