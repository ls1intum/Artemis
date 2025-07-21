package de.tum.cit.aet.artemis.programming.service.hyperion.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO for Hyperion solution generation request
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record HyperionSolutionGenerationRequest(@JsonProperty("boundary_conditions") BoundaryConditions boundaryConditions,
        @JsonProperty("problem_statement") ProblemStatement problemStatement) {

    /**
     * Boundary conditions for the solution generation
     */
    public record BoundaryConditions(@JsonProperty("programming_language") String programmingLanguage, @JsonProperty("project_type") String projectType, String difficulty,
            int points, @JsonProperty("bonus_points") int bonusPoints, List<String> constraints) {
    }

    /**
     * Problem statement for the solution generation
     */
    public record ProblemStatement(String title, @JsonProperty("short_title") String shortTitle, String description) {
    }
}
