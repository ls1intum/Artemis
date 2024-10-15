package de.tum.cit.aet.artemis.iris.service.pyris.dto.chat.textexercise;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.iris.service.pyris.dto.PyrisPipelineExecutionDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisMessageDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisTextExerciseDTO;

/**
 * DTO to execute the text exercise chat pipeline on Pyris.
 *
 * @param execution         pipeline execution details
 * @param exercise          the text exercise being chatted about
 * @param conversation      the chat history
 * @param currentSubmission the user's current submission to the exercise
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
// @formatter:off
public record PyrisTextExerciseChatPipelineExecutionDTO(
        PyrisPipelineExecutionDTO execution,
        PyrisTextExerciseDTO exercise,
        List<PyrisMessageDTO> conversation,
        String currentSubmission
) {}
// @formatter:on
