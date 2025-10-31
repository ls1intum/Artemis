package de.tum.cit.aet.artemis.iris.domain.settings;

import java.io.Serializable;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.Min;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Rate limit overrides stored alongside the course settings.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisRateLimitConfiguration(@Nullable @Min(0) Integer requests, @Nullable @Min(0) Integer timeframeHours) implements Serializable {

    private static final IrisRateLimitConfiguration EMPTY = new IrisRateLimitConfiguration(null, null);

    @JsonCreator
    public IrisRateLimitConfiguration(@JsonProperty("requests") @Nullable @Min(0) Integer requests, @JsonProperty("timeframeHours") @Nullable @Min(0) Integer timeframeHours) {
        this.requests = requests;
        this.timeframeHours = timeframeHours;
    }

    public static IrisRateLimitConfiguration empty() {
        return EMPTY;
    }

    public boolean hasOverride() {
        return requests != null || timeframeHours != null;
    }
}
