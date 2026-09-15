package de.tum.cit.aet.artemis.iris.service.pyris.dto.search;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisRunState;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStatusErrorDTO;

/**
 * Webhook payload sent by Pyris to Artemis at
 * POST /api/iris/internal/pipelines/global-search/runs/{runId}/status.
 *
 * <p>
 * Pyris sends multiple webhooks per request:
 * <ol>
 * <li>Thinking: {@code runState == RUNNING}, {@code answer == null}</li>
 * <li>Streaming drafts: {@code runState == RUNNING} with {@code partialResult}/{@code partialSeq} while the LLM generates</li>
 * <li>Result: terminal {@code runState}, {@code answer} is the LLM response (or null for nav queries)</li>
 * </ol>
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisGlobalSearchAnswerStatusUpdateDTO(PyrisRunState runState, @Nullable PyrisStatusErrorDTO error, @Nullable String answer,
        @Nullable List<PyrisLectureSearchResultDTO> sources, @Nullable String partialResult, @Nullable Integer partialSeq) {

    public PyrisGlobalSearchAnswerStatusUpdateDTO(PyrisRunState runState, @Nullable PyrisStatusErrorDTO error, @Nullable String answer,
            @Nullable List<PyrisLectureSearchResultDTO> sources) {
        this(runState, error, answer, sources, null, null);
    }
}
