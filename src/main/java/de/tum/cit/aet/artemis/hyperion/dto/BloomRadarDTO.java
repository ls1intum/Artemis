package de.tum.cit.aet.artemis.hyperion.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

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
public record BloomRadarDTO(@JsonProperty("REMEMBER") Double remember, @JsonProperty("UNDERSTAND") Double understand, @JsonProperty("APPLY") Double apply,
        @JsonProperty("ANALYZE") Double analyze, @JsonProperty("EVALUATE") Double evaluate, @JsonProperty("CREATE") Double create) {

    /**
     * Creates an empty radar with all zeros.
     */
    public static BloomRadarDTO empty() {
        return new BloomRadarDTO(0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
    }
}
