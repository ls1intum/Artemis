package de.tum.cit.aet.artemis.hyperion.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO for Bloom's taxonomy radar distribution.
 * Values are normalized (0.0 to 1.0) and sum to 1.0.
 *
 * @param remember   Proportion of REMEMBER level
 * @param understand Proportion of UNDERSTAND level
 * @param apply      Proportion of APPLY level
 * @param analyze    Proportion of ANALYZE level
 * @param evaluate   Proportion of EVALUATE level
 * @param create     Proportion of CREATE level
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "Bloom's taxonomy radar distribution with normalized proportions (0.0\u20131.0, summing to 1.0)")
public record BloomRadarDTO(@JsonProperty("REMEMBER") @Schema(description = "Proportion of REMEMBER level (0.0\u20131.0)") double remember,
        @JsonProperty("UNDERSTAND") @Schema(description = "Proportion of UNDERSTAND level (0.0\u20131.0)") double understand,
        @JsonProperty("APPLY") @Schema(description = "Proportion of APPLY level (0.0\u20131.0)") double apply,
        @JsonProperty("ANALYZE") @Schema(description = "Proportion of ANALYZE level (0.0\u20131.0)") double analyze,
        @JsonProperty("EVALUATE") @Schema(description = "Proportion of EVALUATE level (0.0\u20131.0)") double evaluate,
        @JsonProperty("CREATE") @Schema(description = "Proportion of CREATE level (0.0\u20131.0)") double create) {

    /**
     * Creates an empty radar with all zeros.
     */
    public static BloomRadarDTO empty() {
        return new BloomRadarDTO(0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
    }
}
