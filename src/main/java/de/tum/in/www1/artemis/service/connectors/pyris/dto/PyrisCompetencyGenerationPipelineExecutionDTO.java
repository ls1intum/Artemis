package de.tum.in.www1.artemis.service.connectors.pyris.dto;

public class PyrisCompetencyGenerationPipelineExecutionDTO extends PyrisPipelineExecutionDTO {

    private final String courseDescription;

    public PyrisCompetencyGenerationPipelineExecutionDTO(PyrisPipelineExecutionSettingsDTO settings, String courseDescription) {
        super(settings);
        this.courseDescription = courseDescription;
    }

    public String getCourseDescription() {
        return courseDescription;
    }
}
