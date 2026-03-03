package de.tum.cit.aet.artemis.hyperion.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO for consistency check cost's response.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "Response containing consistency check costs")
public record CostsDTO(

        @Schema(description = "Prompt costs in EUR", example = "0.011224") double promptEur,

        @Schema(description = "Completion costs in EUR", example = "0.016513") double completionEur,

        @Schema(description = "Total costs in EUR", example = "0.027738") double totalEur) {
}
