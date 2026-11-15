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
 * <li>Thinking: {@code runState == RUNNING}, {@code answer == null}, optionally {@code stage}</li>
 * <li>Streaming drafts: {@code runState == RUNNING} with {@code partialResult}/{@code partialSeq} while the LLM generates</li>
 * <li>Result: terminal {@code runState}, {@code answer} is the LLM response (or null for nav queries)</li>
 * </ol>
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisGlobalSearchAnswerStatusUpdateDTO(PyrisRunState runState, @Nullable PyrisStatusErrorDTO error, @Nullable String answer,
        @Nullable List<PyrisLectureSearchResultDTO> sources, @Nullable String partialResult, @Nullable Integer partialSeq, @Nullable List<PyrisEntitySourceDTO> entitySources,
        // Short stage name ("searching", "ranking", "found", "generating") sent alongside a thinking update with no partialResult yet.
        // Optional: an older Pyris that never sends it deserializes to null here, same as any other missing field.
        @Nullable String stage,
        // Distinct course names found so far, in ranked order — empty before retrieval finishes, populated once the
        // "generating" stage fires. Optional, same as stage.
        @Nullable List<String> stageSources,
        // For marker 1..N in the terminal answer's citation numbering, which of sources/entitySources that marker
        // resolves into ("lecture" or "entity") — the two arrays are only ordered relative to their OWN type, so a
        // consumer cannot otherwise tell which array a given marker belongs to once citations interleave between
        // types. Optional, same as stage: only ever set on the terminal update, alongside sources/entitySources.
        @Nullable List<String> citationSourceTypes) {

    public PyrisGlobalSearchAnswerStatusUpdateDTO(PyrisRunState runState, @Nullable PyrisStatusErrorDTO error, @Nullable String answer,
            @Nullable List<PyrisLectureSearchResultDTO> sources) {
        this(runState, error, answer, sources, null, null, null, null, null, null);
    }

    public PyrisGlobalSearchAnswerStatusUpdateDTO(PyrisRunState runState, @Nullable PyrisStatusErrorDTO error, @Nullable String answer,
            @Nullable List<PyrisLectureSearchResultDTO> sources, @Nullable String partialResult, @Nullable Integer partialSeq) {
        this(runState, error, answer, sources, partialResult, partialSeq, null, null, null, null);
    }

    public PyrisGlobalSearchAnswerStatusUpdateDTO(PyrisRunState runState, @Nullable PyrisStatusErrorDTO error, @Nullable String answer,
            @Nullable List<PyrisLectureSearchResultDTO> sources, @Nullable String partialResult, @Nullable Integer partialSeq, @Nullable List<PyrisEntitySourceDTO> entitySources) {
        this(runState, error, answer, sources, partialResult, partialSeq, entitySources, null, null, null);
    }

    public PyrisGlobalSearchAnswerStatusUpdateDTO(PyrisRunState runState, @Nullable PyrisStatusErrorDTO error, @Nullable String answer,
            @Nullable List<PyrisLectureSearchResultDTO> sources, @Nullable String partialResult, @Nullable Integer partialSeq, @Nullable List<PyrisEntitySourceDTO> entitySources,
            @Nullable String stage) {
        this(runState, error, answer, sources, partialResult, partialSeq, entitySources, stage, null, null);
    }

    public PyrisGlobalSearchAnswerStatusUpdateDTO(PyrisRunState runState, @Nullable PyrisStatusErrorDTO error, @Nullable String answer,
            @Nullable List<PyrisLectureSearchResultDTO> sources, @Nullable String partialResult, @Nullable Integer partialSeq, @Nullable List<PyrisEntitySourceDTO> entitySources,
            @Nullable String stage, @Nullable List<String> stageSources) {
        this(runState, error, answer, sources, partialResult, partialSeq, entitySources, stage, stageSources, null);
    }
}
