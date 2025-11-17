package de.tum.cit.aet.artemis.iris.service.pyris.dto.chat.lecture;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStageDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisLectureChatStatusUpdateDTO(String result, List<PyrisStageDTO> stages, @Nullable String sessionTitle) {
}
