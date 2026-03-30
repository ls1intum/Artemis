package de.tum.cit.aet.artemis.iris.service.pyris.dto.chat;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import de.tum.cit.aet.artemis.admin.domain.LLMRequest;
import de.tum.cit.aet.artemis.iris.dto.MemirisMemoryDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisActivityDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisRunState;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStatusErrorDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisVerdictDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisChatStatusUpdateDTO(@Nullable String result, PyrisRunState runState, @Nullable PyrisStatusErrorDTO error, @Nullable String sessionTitle,
        @Nullable List<String> suggestions, @Nullable List<LLMRequest> tokens, @Nullable List<MemirisMemoryDTO> accessedMemories, @Nullable List<MemirisMemoryDTO> createdMemories,
        @Nullable String partialResult, @Nullable Integer partialSeq, @Nullable List<PyrisActivityDTO> activities, @Nullable Integer activitySeq,
        @JsonProperty("final") @Nullable Boolean finalResult,  @Nullable String event, @Nullable IrisVerdictDTO verdict) {

    public PyrisChatStatusUpdateDTO(@Nullable String result, PyrisRunState runState, @Nullable PyrisStatusErrorDTO error, @Nullable String sessionTitle,
            @Nullable List<String> suggestions, @Nullable List<LLMRequest> tokens, @Nullable List<MemirisMemoryDTO> accessedMemories,
            @Nullable List<MemirisMemoryDTO> createdMemories, @Nullable String partialResult, @Nullable Integer partialSeq, @Nullable List<PyrisActivityDTO> activities,
            @Nullable Integer activitySeq) {
        this(result, runState, error, sessionTitle, suggestions, tokens, accessedMemories, createdMemories, partialResult, partialSeq, activities, activitySeq, null, null, null);
    }

    public PyrisChatStatusUpdateDTO(@Nullable String result, PyrisRunState runState, @Nullable PyrisStatusErrorDTO error, @Nullable String sessionTitle,
            @Nullable List<String> suggestions, @Nullable List<LLMRequest> tokens, @Nullable List<MemirisMemoryDTO> accessedMemories,
            @Nullable List<MemirisMemoryDTO> createdMemories) {
        this(result, runState, error, sessionTitle, suggestions, tokens, accessedMemories, createdMemories, null, null, null, null, null, null, null);
    }
}
