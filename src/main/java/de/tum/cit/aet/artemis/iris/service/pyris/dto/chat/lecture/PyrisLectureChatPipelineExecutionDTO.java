package de.tum.cit.aet.artemis.iris.service.pyris.dto.chat.lecture;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.iris.service.pyris.dto.PyrisPipelineExecutionSettingsDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisCourseDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisLectureDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisMessageDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisUserDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStageDTO;

/**
 * DTO for executing the lecture chat pipeline.
 * Uses DTO-based objects (course, lecture) instead of raw IDs for richer context.
 *
 * @param course             the course this chat belongs to
 * @param lecture            the lecture this chat is about
 * @param sessionTitle       the current session title
 * @param chatHistory        the list of previous messages in this session
 * @param user               the user interacting with the chat
 * @param settings           pipeline execution settings (job token, etc.)
 * @param initialStages      initial pipeline stages for status tracking
 * @param customInstructions optional custom instructions for the LLM
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisLectureChatPipelineExecutionDTO(PyrisCourseDTO course, PyrisLectureDTO lecture, String sessionTitle, List<PyrisMessageDTO> chatHistory, PyrisUserDTO user,
        PyrisPipelineExecutionSettingsDTO settings, List<PyrisStageDTO> initialStages, @Nullable String customInstructions) {
}
