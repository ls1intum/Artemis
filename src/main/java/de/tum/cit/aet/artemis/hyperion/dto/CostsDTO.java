package de.tum.cit.aet.artemis.hyperion.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO for consistency check cost's response.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "Response containing consistency check costs")
public record CostsDTO(

        @Schema(description = "Prompt costs in USD", example = "0.011224") double promptUsd,

        @Schema(description = "Completion costs in USD", example = "0.016513") double completionUsd,

        @Schema(description = "Total costs in USD", example = "0.027738") double totalUsd) {
}
