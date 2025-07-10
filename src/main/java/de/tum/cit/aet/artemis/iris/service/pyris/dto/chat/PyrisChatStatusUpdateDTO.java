package de.tum.cit.aet.artemis.iris.service.pyris.dto.chat;

import java.util.List;

import jakarta.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.LLMRequest;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStageDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisChatStatusUpdateDTO(@Nullable String result, List<PyrisStageDTO> stages, List<String> suggestions, @Nullable List<LLMRequest> tokens) {
}
