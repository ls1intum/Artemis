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
        boolean failed,
        // Short stage name ("searching", "ranking", "found", "generating") the client shows in place of its generic "thinking" message.
        // Only ever set on a thinking update with no partialResult yet — once the answer starts streaming, the
        // arriving text is its own progress signal.
        @Nullable String stage,
        // Distinct course names found so far, in ranked order — empty before retrieval finishes, populated once the
        // "generating" stage fires, so the client can say what it actually found instead of a generic message.
        @Nullable List<String> stageSources,
        // For marker 1..N in `answer`'s citation numbering, which of sources/entitySources that marker resolves
        // into ("lecture" or "entity") — the two arrays are only ordered relative to their OWN type, so the client
        // cannot otherwise tell which array a given marker belongs to once citations interleave between types.
        // Only ever set on a terminal (isThinking=false) update, alongside sources/entitySources.
        @Nullable List<String> citationSourceTypes) {

    public IrisGlobalSearchAnswerWebsocketDTO(String runId, boolean isThinking, @Nullable String answer, @Nullable List<PyrisLectureSearchResultDTO> sources) {
        this(runId, isThinking, answer, sources, null, null, null, false, false, null, null, null);
    }

    public IrisGlobalSearchAnswerWebsocketDTO(String runId, boolean isThinking, @Nullable String answer, @Nullable List<PyrisLectureSearchResultDTO> sources,
            @Nullable String partialResult, @Nullable Integer partialSeq, boolean clearDraft) {
        this(runId, isThinking, answer, sources, partialResult, partialSeq, null, clearDraft, false, null, null, null);
    }

    /**
     * A thinking update with a stage name (and the course names found so far) and nothing else yet — sent as
     * retrieval/generation cross a boundary. A constructor overload here would clash with the 4-arg
     * (runId, isThinking, answer, sources) one above: List&lt;String&gt; and List&lt;PyrisLectureSearchResultDTO&gt;
     * erase to the same raw List type, so a static factory is used instead.
     */
    public static IrisGlobalSearchAnswerWebsocketDTO thinking(String runId, @Nullable String stage, @Nullable List<String> stageSources) {
        return new IrisGlobalSearchAnswerWebsocketDTO(runId, true, null, null, null, null, null, false, false, stage, stageSources, null);
    }
}
