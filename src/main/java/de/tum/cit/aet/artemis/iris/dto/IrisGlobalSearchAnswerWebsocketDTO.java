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
 * <li>{@code isThinking=false}: Pipeline finished. Show the {@code answer} card if non-null and {@code failed} is
 * false; show the retryable failure state if {@code failed} is true; hide otherwise.</li>
 * </ul>
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisGlobalSearchAnswerWebsocketDTO(String runId, boolean isThinking, @Nullable String answer, @Nullable List<PyrisLectureSearchResultDTO> sources,
        @Nullable String partialResult, @Nullable Integer partialSeq, @Nullable List<PyrisEntitySourceDTO> entitySources,
        // Distinguishes the provider's retry-clear signal for a stale streamed draft (see PartialResultSender on the
        // Pyris side) from "no partial result in this message". An empty partialResult cannot carry that meaning
        // itself: the record-level NON_EMPTY policy drops null and an empty string identically, so the client could
        // not otherwise tell "clear the draft" from "nothing new this frame".
        boolean clearDraft,
        // Distinguishes a genuine Pyris-side failure from a successful run with no relevant answer: both otherwise
        // produce isThinking=false, answer=null, which the client would show as "nothing relevant" with no retry.
        boolean failed) {

    public IrisGlobalSearchAnswerWebsocketDTO(String runId, boolean isThinking, @Nullable String answer, @Nullable List<PyrisLectureSearchResultDTO> sources) {
        this(runId, isThinking, answer, sources, null, null, null, false, false);
    }

    public IrisGlobalSearchAnswerWebsocketDTO(String runId, boolean isThinking, @Nullable String answer, @Nullable List<PyrisLectureSearchResultDTO> sources,
            @Nullable String partialResult, @Nullable Integer partialSeq, boolean clearDraft) {
        this(runId, isThinking, answer, sources, partialResult, partialSeq, null, clearDraft, false);
    }
}
