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
        @Nullable PyrisAccessContextDTO accessContext, @Nullable List<PyrisEntityCandidateDTO> entityCandidates, @Nullable List<Long> courseIds,
        // Only needed for a caller with no courseIds ceiling to narrow itself (unrestricted access): every other
        // caller already has exclusions baked into courseIds by lectureSearchScope.
        @Nullable List<Long> excludeCourseIds,
        // Disambiguates an all-excluded course scope from "unscoped" without relying on an empty courseIds list
        // surviving the wire: the class-level NON_EMPTY policy drops both null and an empty list identically, so
        // Pyris could not otherwise tell "no scope requested" from "every requested course was excluded".
        boolean searchesNothing) {
}
