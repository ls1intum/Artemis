package de.tum.cit.aet.artemis.iris.dto;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.iris.service.pyris.dto.search.PyrisEntitySourceDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.search.PyrisLectureSearchResultDTO;

/**
 * WebSocket message sent from Artemis to the Angular client for global search Iris answer status updates.
 *
 * <p>
 * There are two kinds of messages:
 * <ul>
 * <li>{@code isThinking=true}: Pyris classified the query as a real question; LLM is running. When
 * {@code partialResult} is set, it carries the streamed draft of the answer so far ({@code partialSeq} is monotonic).</li>
 * <li>{@code isThinking=false}: Pipeline finished. Show the {@code answer} card if non-null; hide otherwise.</li>
 * </ul>
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisGlobalSearchAnswerWebsocketDTO(String runId, boolean isThinking, @Nullable String answer, @Nullable List<PyrisLectureSearchResultDTO> sources,
        // NON_NULL, not the record-level NON_EMPTY: an empty string is the provider's retry-clear signal for a stale
        // streamed draft (see PartialResultSender on the Pyris side) and must reach the client distinguishable from
        // "no partial result in this message", which NON_EMPTY would otherwise collapse it into.
        @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable String partialResult, @Nullable Integer partialSeq, @Nullable List<PyrisEntitySourceDTO> entitySources) {

    public IrisGlobalSearchAnswerWebsocketDTO(String runId, boolean isThinking, @Nullable String answer, @Nullable List<PyrisLectureSearchResultDTO> sources) {
        this(runId, isThinking, answer, sources, null, null, null);
    }

    public IrisGlobalSearchAnswerWebsocketDTO(String runId, boolean isThinking, @Nullable String answer, @Nullable List<PyrisLectureSearchResultDTO> sources,
            @Nullable String partialResult, @Nullable Integer partialSeq) {
        this(runId, isThinking, answer, sources, partialResult, partialSeq, null);
    }
}
