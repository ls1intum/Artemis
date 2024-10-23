package de.tum.cit.aet.artemis.iris.service.pyris.dto.chat.exercise;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.iris.service.pyris.dto.PyrisPipelineExecutionSettingsDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisCourseDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisMessageDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisProgrammingExerciseDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisSubmissionDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisUserDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStageDTO;

/**
 * DTO for executing the programming exercise chat pipeline on Pyris.
 *
 * @param submission    the user's latest programming submission, if any.
 * @param exercise      the programming exercise being chatted about
 * @param course        the course
 * @param chatHistory   the conversation history so far (including the user's latest message)
 * @param user          the user
 * @param settings      execution settings
 * @param initialStages the initial stages from pipeline preparation
 */
// TODO: Instead of including settings and initialStages, just include PyrisPipelineExecutionDTO (requires a change on Pyris)
// @formatter:off
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisExerciseChatPipelineExecutionDTO(
        PyrisSubmissionDTO submission, // TODO: could wrap in Optional for API clarity
        PyrisProgrammingExerciseDTO exercise,
        PyrisCourseDTO course,
        List<PyrisMessageDTO> chatHistory,
        PyrisUserDTO user,
        PyrisPipelineExecutionSettingsDTO settings,
        List<PyrisStageDTO> initialStages
) {}
// @formatter:on
