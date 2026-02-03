package de.tum.cit.aet.artemis.hyperion.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for the response of the checklist analysis.
 *
 * @param inferredCompetencies Top 5 inferred competencies from the standardized
 *                                 catalog
 * @param bloomRadar           Bloom's taxonomy distribution for radar chart
 * @param difficultyAssessment Difficulty assessment with delta indicator
 * @param qualityIssues        Quality issues found in the problem statement
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ChecklistAnalysisResponseDTO(List<InferredCompetencyDTO> inferredCompetencies, BloomRadarDTO bloomRadar, DifficultyAssessmentDTO difficultyAssessment,
        List<QualityIssueDTO> qualityIssues) {

    /**
     * Creates an empty response (when analysis fails).
     */
    public static ChecklistAnalysisResponseDTO empty() {
        return new ChecklistAnalysisResponseDTO(List.of(), BloomRadarDTO.empty(), null, List.of());
    }
}
