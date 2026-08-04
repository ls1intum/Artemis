package de.tum.cit.aet.artemis.iris.service.pyris.dto.chat.askuser;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.iris.domain.session.IrisChatMode;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.PyrisPipelineExecutionSettingsDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisCourseDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisMessageDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisProgrammingExerciseDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisSubmissionDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisUserDTO;

/**
 * Represents the execution of the ask-user-mode pipeline for a programming exercise chat session.
 *
 * @param chatMode                      the chat mode of the session
 * @param chatHistory                   chat history of the session
 * @param settings                      pipeline settings
 * @param sessionTitle                  optional title of the session
 * @param user                          user that is being asked questions
 * @param course                        courseDTO of the course the exercise is in
 * @param programmingExercise           the programming exercise the questions relate to
 * @param programmingExerciseSubmission the student's latest submission for the exercise
 * @param minQuestions                  the minimum number of questions to ask before concluding
 * @param maxQuestions                  the maximum number of questions allowed
 * @param questionsAsked                the number of questions already asked in this run
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisAskUserPipelineExecutionDTO(IrisChatMode chatMode, List<PyrisMessageDTO> chatHistory, PyrisPipelineExecutionSettingsDTO settings, @Nullable String sessionTitle,
        PyrisUserDTO user, PyrisCourseDTO course, PyrisProgrammingExerciseDTO programmingExercise, PyrisSubmissionDTO programmingExerciseSubmission, int minQuestions,
        int maxQuestions, int questionsAsked) {

    public PyrisSubmissionDTO programmingExerciseSubmission() {
        return programmingExerciseSubmission;
    }
}
