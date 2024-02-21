package de.tum.in.www1.artemis.service.connectors.iris.dto;

import java.util.List;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.iris.message.IrisMessage;

public class PyrisCodeEditorPipelineExecutionDTO extends PyrisPipelineExecutionDTO {

    private final ProgrammingExercise exercise;

    private final List<IrisMessage> chatHistory;

    public PyrisCodeEditorPipelineExecutionDTO(PyrisPipelineExecutionSettingsDTO settings, ProgrammingExercise exercise, List<IrisMessage> chatHistory) {
        super(settings);
        this.exercise = exercise;
        this.chatHistory = chatHistory;
    }

    public ProgrammingExercise getExercise() {
        return exercise;
    }

    public List<IrisMessage> getChatHistory() {
        return chatHistory;
    }
}
