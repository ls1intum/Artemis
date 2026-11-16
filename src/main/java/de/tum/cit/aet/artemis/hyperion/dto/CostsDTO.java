package de.tum.cit.aet.artemis.hyperion.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO for consistency check cost's response.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "Response containing consistency check costs")
public record CostsDTO(@Schema(description = "Known cost in USD; a lower bound when incomplete", example = "0.027738") @Nullable Double totalUsd,
        @Schema(description = "Whether every recorded request has a USD cost", requiredMode = Schema.RequiredMode.REQUIRED) boolean totalUsdComplete) {
}
