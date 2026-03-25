package de.tum.cit.aet.artemis.hyperion.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO for the response of the checklist analysis.
 *
 * @param inferredCompetencies Top 5 inferred competencies from the standardized catalog
 * @param bloomRadar           Bloom's taxonomy distribution for radar chart
 * @param difficultyAssessment Difficulty assessment with delta indicator
 * @param qualityIssues        Quality issues found in the problem statement
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "Response containing the checklist analysis results")
public record ChecklistAnalysisResponseDTO(@Schema(description = "Top inferred competencies from the standardized catalog") List<InferredCompetencyDTO> inferredCompetencies,
        @Schema(description = "Bloom's taxonomy radar distribution") BloomRadarDTO bloomRadar,
        @Schema(description = "Difficulty assessment with delta indicator") DifficultyAssessmentDTO difficultyAssessment,
        @Schema(description = "List of quality issues found in the problem statement") List<QualityIssueDTO> qualityIssues) {

    /**
     * Creates an empty response (when analysis fails).
     */
    public static ChecklistAnalysisResponseDTO empty() {
        return new ChecklistAnalysisResponseDTO(List.of(), BloomRadarDTO.empty(), null, List.of());
    }
}
