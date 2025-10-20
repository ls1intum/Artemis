package de.tum.cit.aet.artemis.core.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.config.RateLimitingProperties;
import de.tum.cit.aet.artemis.core.security.RateLimitType;

/**
 * Service for managing rate limiting configuration.
 * Provides centralized access to rate limiting settings including enable/disable flags
 * and configurable RPM values for different endpoint types.
 */
@Profile(PROFILE_CORE)
@Service
@Lazy
public class RateLimitConfigurationService {

    private final RateLimitingProperties properties;

    public RateLimitConfigurationService(RateLimitingProperties properties) {
        this.properties = properties;
    }

    /**
     * Checks if rate limiting is enabled globally.
     *
     * @return true if rate limiting is enabled, false otherwise (default: false)
     */
    public boolean isRateLimitingEnabled() {
        return properties.isEnabled();
    }

    /**
     * Gets the effective RPM value for a given rate limit type.
     * Returns the configured value if available, otherwise falls back to the default.
     *
     * @param type the rate limit type
     * @return the effective RPM value
     */
    public int getEffectiveRpm(RateLimitType type) {
        return switch (type) {
            case PUBLIC -> properties.getPublicRpm() != null ? properties.getPublicRpm() : type.getDefaultRpm();
            case LOGIN_RELATED -> properties.getLoginRelatedRpm() != null ? properties.getLoginRelatedRpm() : type.getDefaultRpm();
        };
    }
}
