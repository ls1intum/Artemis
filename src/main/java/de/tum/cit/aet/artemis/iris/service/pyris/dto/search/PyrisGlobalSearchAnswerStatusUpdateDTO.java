package de.tum.cit.aet.artemis.iris.service.pyris.dto.search;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStageDTO;

/**
 * Webhook payload sent by Pyris to Artemis at
 * POST /api/iris/internal/pipelines/global-search/runs/{runId}/status.
 *
 * <p>
 * Pyris sends two webhooks per request:
 * <ol>
 * <li>Thinking: {@code stages[0].state == IN_PROGRESS}, {@code answer == null}</li>
 * <li>Result: all stages terminal, {@code answer} is the LLM response (or null for nav queries)</li>
 * </ol>
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisGlobalSearchAnswerStatusUpdateDTO(List<PyrisStageDTO> stages, @Nullable String answer, @Nullable List<PyrisGlobalSearchSourceDTO> sources) {
}
