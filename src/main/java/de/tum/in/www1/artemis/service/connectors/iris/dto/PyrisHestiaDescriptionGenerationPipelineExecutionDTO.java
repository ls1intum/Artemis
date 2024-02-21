package de.tum.in.www1.artemis.service.connectors.iris.dto;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.hestia.CodeHint;

public class PyrisHestiaDescriptionGenerationPipelineExecutionDTO extends PyrisPipelineExecutionDTO {

    private final ProgrammingExercise exercise;

    private final CodeHint codeHint;

    public PyrisHestiaDescriptionGenerationPipelineExecutionDTO(PyrisPipelineExecutionSettingsDTO settings, ProgrammingExercise exercise, CodeHint codeHint) {
        super(settings);
        this.exercise = exercise;
        this.codeHint = codeHint;
    }

    public ProgrammingExercise getExercise() {
        return exercise;
    }

    public CodeHint getCodeHint() {
        return codeHint;
    }
}
