package de.tum.cit.aet.artemis.hyperion.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for the response of the checklist analysis.
 *
 * @param bloomRadar    Bloom's taxonomy distribution for radar chart
 * @param qualityIssues Quality issues found in the problem statement
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ChecklistAnalysisResponseDTO(BloomRadarDTO bloomRadar, List<QualityIssueDTO> qualityIssues) {

    /**
     * Creates an empty response (when analysis fails).
     */
    public static ChecklistAnalysisResponseDTO empty() {
        return new ChecklistAnalysisResponseDTO(BloomRadarDTO.empty(), List.of());
    }
}
