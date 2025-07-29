package de.tum.cit.aet.artemis.hyperion.dto;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO for individual consistency issues.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "Individual consistency issue details")
public record ConsistencyIssueDTO(@NotNull @Schema(description = "Severity of the issue", example = "HIGH", allowableValues = {
        "LOW", "MEDIUM", "HIGH" }) String severity,

        @NotNull @Schema(description = "Category of the issue", example = "PROBLEM_STATEMENT_MISMATCH") String category,

        @NotNull @Schema(description = "Detailed description of the issue", example = "Problem statement does not match test cases") String description,

        @NotNull @Schema(description = "Suggested fix for the issue", example = "Update problem statement to clarify expected behavior") String suggestedFix){
}
