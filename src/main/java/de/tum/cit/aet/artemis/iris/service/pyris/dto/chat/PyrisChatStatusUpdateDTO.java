package de.tum.cit.aet.artemis.iris.service.pyris.dto.chat;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStageDTO;

/**
 * A DTO to send chat-based Pyris pipeline status updates to the client.
 *
 * @param result      Iris' textual response, if any. TODO: Wrap in Optional for API clarity
 * @param stages      The pipeline execution progress described in a list of stages
 * @param suggestions A list of suggested next inputs for the user to click on
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisChatStatusUpdateDTO(String result, List<PyrisStageDTO> stages, List<String> suggestions) {
}
