package de.tum.cit.aet.artemis.iris.service.pyris.dto.search;

import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.iris.service.pyris.dto.PyrisPipelineExecutionSettingsDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStageDTO;

/**
 * Request body sent to Pyris POST /api/v1/pipelines/global-search/run (async, returns 202).
 * Pyris sends two webhook callbacks back to Artemis:
 * 1. A "thinking" callback when it decides the query requires an LLM answer.
 * 2. A "result" callback when the pipeline finishes, with the final answer (or null).
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisGlobalSearchAnswerRequestDTO(@NotBlank String query, @Min(1) @Max(5) int limit, PyrisPipelineExecutionSettingsDTO settings, List<PyrisStageDTO> initialStages) {
}
