package de.tum.cit.aet.artemis.hyperion.dto;

import java.util.List;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO for consistency check responses.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "Response containing consistency check results")
public record ConsistencyCheckResponseDTO(@NotNull @Schema(description = "List of consistency issues found") List<ConsistencyIssueDTO> issues,

        @NotNull @Schema(description = "Whether any issues were found", example = "true") Boolean hasIssues,

        @NotNull @Schema(description = "Summary of the consistency check", example = "Found 3 consistency issues") String summary) {
}
