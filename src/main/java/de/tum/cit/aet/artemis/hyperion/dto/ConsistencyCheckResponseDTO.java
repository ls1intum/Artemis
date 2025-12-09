package de.tum.cit.aet.artemis.hyperion.dto;

import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO for consistency check responses.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "Response containing consistency check results")
public record ConsistencyCheckResponseDTO(

        @NotNull @Schema(description = "Timestamp of the response generation") Instant timestamp,

        @NotNull @Schema(description = "List of consistency issues found") List<ConsistencyIssueDTO> issues,

        @Schema(description = "Execution timing details") TimingDTO timing,

        @Schema(description = "Token usage statistics") TokensDTO tokens) {
}
