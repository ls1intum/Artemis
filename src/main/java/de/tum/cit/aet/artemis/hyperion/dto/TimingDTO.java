package de.tum.cit.aet.artemis.hyperion.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO for consistency check timing response.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "Response containing consistency check timing")
public record TimingDTO(

        @Schema(description = "Starting time", example = "2025-07-27T07:17:05.500459") String startTime,

        @Schema(description = "Ending time", example = "2025-07-27T07:17:05.500459") String endTime,

        @Schema(description = "Duration of consistency check", example = "27.765") double durationS) {
}
