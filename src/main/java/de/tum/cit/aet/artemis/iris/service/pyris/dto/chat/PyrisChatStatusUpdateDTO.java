package de.tum.cit.aet.artemis.iris.service.pyris.dto.chat;

import java.util.List;

import jakarta.validation.Valid;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import de.tum.cit.aet.artemis.admin.domain.LLMRequest;
import de.tum.cit.aet.artemis.iris.dto.IrisVerdictDTO;
import de.tum.cit.aet.artemis.iris.dto.MemirisMemoryDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisActivityDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisRunState;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStatusErrorDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisChatStatusUpdateDTO(@Nullable String result, PyrisRunState runState, @Nullable PyrisStatusErrorDTO error, @Nullable String sessionTitle,
        @Nullable List<String> suggestions, @Nullable List<LLMRequest> tokens, @Nullable List<MemirisMemoryDTO> accessedMemories, @Nullable List<MemirisMemoryDTO> createdMemories,
        @Nullable String partialResult, @Nullable Integer partialSeq, @Nullable List<PyrisActivityDTO> activities, @Nullable Integer activitySeq,
        @JsonProperty("final") @Nullable Boolean finalResult, @Nullable String event, @Nullable @Valid IrisVerdictDTO verdict) {

    /**
     * Creates a status update without a final result, event, or verdict, for backwards compatibility with callers
     * that only report partial/activity progress.
     *
     * @param result           the result text, if any
     * @param runState         the current run state
     * @param error            the error, if any
     * @param sessionTitle     the (generated) session title, if any
     * @param suggestions      follow-up suggestions, if any
     * @param tokens           the LLM requests made during this update, if any
     * @param accessedMemories the memories accessed during this update, if any
     * @param createdMemories  the memories created during this update, if any
     * @param partialResult    a partial result chunk, if any
     * @param partialSeq       the sequence number of the partial result, if any
     * @param activities       the activities reported during this update, if any
     * @param activitySeq      the sequence number of the activities update, if any
     */
    public PyrisChatStatusUpdateDTO(@Nullable String result, PyrisRunState runState, @Nullable PyrisStatusErrorDTO error, @Nullable String sessionTitle,
            @Nullable List<String> suggestions, @Nullable List<LLMRequest> tokens, @Nullable List<MemirisMemoryDTO> accessedMemories,
            @Nullable List<MemirisMemoryDTO> createdMemories, @Nullable String partialResult, @Nullable Integer partialSeq, @Nullable List<PyrisActivityDTO> activities,
            @Nullable Integer activitySeq) {
        this(result, runState, error, sessionTitle, suggestions, tokens, accessedMemories, createdMemories, partialResult, partialSeq, activities, activitySeq, null, null, null);
    }

    /**
     * Creates a status update without an event or verdict, for backwards compatibility with callers that report a
     * final result but do not send ask-user-mode events or verdicts.
     *
     * @param result           the result text, if any
     * @param runState         the current run state
     * @param error            the error, if any
     * @param sessionTitle     the (generated) session title, if any
     * @param suggestions      follow-up suggestions, if any
     * @param tokens           the LLM requests made during this update, if any
     * @param accessedMemories the memories accessed during this update, if any
     * @param createdMemories  the memories created during this update, if any
     * @param partialResult    a partial result chunk, if any
     * @param partialSeq       the sequence number of the partial result, if any
     * @param activities       the activities reported during this update, if any
     * @param activitySeq      the sequence number of the activities update, if any
     * @param finalResult      whether this update carries the final result
     */
    public PyrisChatStatusUpdateDTO(@Nullable String result, PyrisRunState runState, @Nullable PyrisStatusErrorDTO error, @Nullable String sessionTitle,
            @Nullable List<String> suggestions, @Nullable List<LLMRequest> tokens, @Nullable List<MemirisMemoryDTO> accessedMemories,
            @Nullable List<MemirisMemoryDTO> createdMemories, @Nullable String partialResult, @Nullable Integer partialSeq, @Nullable List<PyrisActivityDTO> activities,
            @Nullable Integer activitySeq, @Nullable Boolean finalResult) {
        this(result, runState, error, sessionTitle, suggestions, tokens, accessedMemories, createdMemories, partialResult, partialSeq, activities, activitySeq, finalResult, null,
                null);
    }

    /**
     * Creates a minimal status update without partial result, activity, final result, event, or verdict information.
     *
     * @param result           the result text, if any
     * @param runState         the current run state
     * @param error            the error, if any
     * @param sessionTitle     the (generated) session title, if any
     * @param suggestions      follow-up suggestions, if any
     * @param tokens           the LLM requests made during this update, if any
     * @param accessedMemories the memories accessed during this update, if any
     * @param createdMemories  the memories created during this update, if any
     */
    public PyrisChatStatusUpdateDTO(@Nullable String result, PyrisRunState runState, @Nullable PyrisStatusErrorDTO error, @Nullable String sessionTitle,
            @Nullable List<String> suggestions, @Nullable List<LLMRequest> tokens, @Nullable List<MemirisMemoryDTO> accessedMemories,
            @Nullable List<MemirisMemoryDTO> createdMemories) {
        this(result, runState, error, sessionTitle, suggestions, tokens, accessedMemories, createdMemories, null, null, null, null, null, null, null);
    }
}
