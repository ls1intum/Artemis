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

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisAskUserPipelineExecutionDTO(IrisChatMode chatMode, List<PyrisMessageDTO> chatHistory, PyrisPipelineExecutionSettingsDTO settings, @Nullable String sessionTitle,
        PyrisUserDTO user, PyrisCourseDTO course, PyrisProgrammingExerciseDTO programmingExercise, PyrisSubmissionDTO programmingExerciseSubmission, int minQuestions,
        int maxQuestions, int questionsAsked) {

    public PyrisSubmissionDTO programmingExerciseSubmission() {
        return programmingExerciseSubmission;
    }
}
