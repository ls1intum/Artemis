package de.tum.cit.aet.artemis.iris.dto;

import jakarta.validation.constraints.Min;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.iris.domain.settings.IrisRateLimitConfiguration;

/**
 * A rate limit configuration at the REST boundary.
 *
 * @param requests       maximum requests in the window, or zero to block requests
 * @param timeframeHours length of the window in hours
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public record IrisRateLimitConfigurationDTO(@Nullable @Min(0) Integer requests, @Nullable @Min(1) Integer timeframeHours) {

    /**
     * @param configuration the domain configuration
     * @return the equivalent REST payload
     */
    public static IrisRateLimitConfigurationDTO of(IrisRateLimitConfiguration configuration) {
        return new IrisRateLimitConfigurationDTO(configuration.requests(), configuration.timeframeHours());
    }

    /**
     * @return the equivalent domain configuration
     */
    public IrisRateLimitConfiguration toDomain() {
        return new IrisRateLimitConfiguration(requests, timeframeHours);
    }
}
