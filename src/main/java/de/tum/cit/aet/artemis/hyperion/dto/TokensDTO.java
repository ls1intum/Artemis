package de.tum.cit.aet.artemis.hyperion.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO for consistency check token's response.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "Response containing consistency check tokens")
public record TokensDTO(

        @Schema(description = "Prompt token length", example = "10204") long prompt,

        @Schema(description = "Completion token length", example = "3753") long completion,

        @Schema(description = "Total token length", example = "13957") long total) {
}
