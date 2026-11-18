package de.tum.cit.aet.artemis.iris.service.pyris.dto.chat;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import de.tum.cit.aet.artemis.admin.domain.LLMRequest;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisActivityDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisRunState;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStatusErrorDTO;

/**
 * A status update Pyris sends while a chat run is in progress.
 * <p>
 * {@code activities} is the snapshot of every step of the run, for the live view. {@code messageActivities} holds the steps that ran since the previous assistant
 * message of the run; it comes with an assistant message (interim or final), which stores exactly these steps, so each message shows its own. Pyris versions
 * without it leave it {@code null}, and the final message then stores the whole run.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisChatStatusUpdateDTO(@Nullable String result, PyrisRunState runState, @Nullable PyrisStatusErrorDTO error, @Nullable String sessionTitle,
        @Nullable List<String> suggestions, @Nullable List<LLMRequest> tokens, @Nullable String partialResult, @Nullable Integer partialSeq,
        @Nullable List<PyrisActivityDTO> activities, @Nullable Integer activitySeq, @JsonProperty("final") @Nullable Boolean finalResult,
        @Nullable PyrisSuggestedContextDTO suggestedContext, @Nullable PyrisCompactionDTO compaction, @Nullable List<PyrisActivityDTO> messageActivities) {

    public PyrisChatStatusUpdateDTO(@Nullable String result, PyrisRunState runState, @Nullable PyrisStatusErrorDTO error, @Nullable String sessionTitle,
            @Nullable List<String> suggestions, @Nullable List<LLMRequest> tokens, @Nullable String partialResult, @Nullable Integer partialSeq,
            @Nullable List<PyrisActivityDTO> activities, @Nullable Integer activitySeq, @Nullable Boolean finalResult, @Nullable PyrisSuggestedContextDTO suggestedContext,
            @Nullable PyrisCompactionDTO compaction) {
        this(result, runState, error, sessionTitle, suggestions, tokens, partialResult, partialSeq, activities, activitySeq, finalResult, suggestedContext, compaction, null);
    }

    public PyrisChatStatusUpdateDTO(@Nullable String result, PyrisRunState runState, @Nullable PyrisStatusErrorDTO error, @Nullable String sessionTitle,
            @Nullable List<String> suggestions, @Nullable List<LLMRequest> tokens, @Nullable String partialResult, @Nullable Integer partialSeq,
            @Nullable List<PyrisActivityDTO> activities, @Nullable Integer activitySeq, @Nullable Boolean finalResult) {
        this(result, runState, error, sessionTitle, suggestions, tokens, partialResult, partialSeq, activities, activitySeq, finalResult, null, null, null);
    }

    public PyrisChatStatusUpdateDTO(@Nullable String result, PyrisRunState runState, @Nullable PyrisStatusErrorDTO error, @Nullable String sessionTitle,
            @Nullable List<String> suggestions, @Nullable List<LLMRequest> tokens, @Nullable String partialResult, @Nullable Integer partialSeq,
            @Nullable List<PyrisActivityDTO> activities, @Nullable Integer activitySeq) {
        this(result, runState, error, sessionTitle, suggestions, tokens, partialResult, partialSeq, activities, activitySeq, null, null, null, null);
    }

    public PyrisChatStatusUpdateDTO(@Nullable String result, PyrisRunState runState, @Nullable PyrisStatusErrorDTO error, @Nullable String sessionTitle,
            @Nullable List<String> suggestions, @Nullable List<LLMRequest> tokens) {
        this(result, runState, error, sessionTitle, suggestions, tokens, null, null, null, null, null, null, null, null);
    }
}
