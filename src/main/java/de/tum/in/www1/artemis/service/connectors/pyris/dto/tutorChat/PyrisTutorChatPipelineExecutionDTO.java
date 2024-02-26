package de.tum.in.www1.artemis.service.connectors.pyris.dto.tutorChat;

import java.util.List;

import de.tum.in.www1.artemis.service.connectors.pyris.dto.PyrisPipelineExecutionDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.PyrisPipelineExecutionSettingsDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.data.PyrisCourseDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.data.PyrisMessageDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.data.PyrisProgrammingExerciseDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.data.PyrisSubmissionDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.data.PyrisUserDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.status.PyrisStageDTO;

public final class PyrisTutorChatPipelineExecutionDTO extends PyrisPipelineExecutionDTO {

    private final PyrisSubmissionDTO submission;

    private final PyrisProgrammingExerciseDTO exercise;

    private final PyrisCourseDTO course;

    private final List<PyrisMessageDTO> chatHistory;

    private final PyrisUserDTO user;

    public PyrisTutorChatPipelineExecutionDTO(PyrisPipelineExecutionSettingsDTO settings, List<PyrisStageDTO> initialStages, PyrisSubmissionDTO submission,
            PyrisProgrammingExerciseDTO exercise, PyrisCourseDTO course, List<PyrisMessageDTO> chatHistory, PyrisUserDTO user) {
        super(settings, initialStages);
        this.submission = submission;
        this.exercise = exercise;
        this.course = course;
        this.chatHistory = chatHistory;
        this.user = user;
    }

    public PyrisSubmissionDTO getSubmission() {
        return submission;
    }

    public PyrisProgrammingExerciseDTO getExercise() {
        return exercise;
    }

    public PyrisCourseDTO getCourse() {
        return course;
    }

    public List<PyrisMessageDTO> getChatHistory() {
        return chatHistory;
    }

    public PyrisUserDTO getUser() {
        return user;
    }
}
