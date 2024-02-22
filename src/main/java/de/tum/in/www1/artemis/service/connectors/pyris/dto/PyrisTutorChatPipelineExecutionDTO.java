package de.tum.in.www1.artemis.service.connectors.pyris.dto;

import java.util.List;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.Submission;
import de.tum.in.www1.artemis.domain.iris.message.IrisMessage;

public class PyrisTutorChatPipelineExecutionDTO extends PyrisPipelineExecutionDTO {

    private final Submission latestSubmission;

    private final ProgrammingExercise exercise;

    private final Course course;

    private final List<IrisMessage> chatHistory;

    public PyrisTutorChatPipelineExecutionDTO(PyrisPipelineExecutionSettingsDTO settings, Submission latestSubmission, ProgrammingExercise exercise,
            List<IrisMessage> chatHistory) {
        super(settings);
        this.latestSubmission = latestSubmission;
        this.exercise = exercise;
        this.course = exercise.getCourseViaExerciseGroupOrCourseMember();
        this.chatHistory = chatHistory;
    }

    public Submission getLatestSubmission() {
        return latestSubmission;
    }

    public ProgrammingExercise getExercise() {
        return exercise;
    }

    public Course getCourse() {
        return course;
    }

    public List<IrisMessage> getChatHistory() {
        return chatHistory;
    }
}
