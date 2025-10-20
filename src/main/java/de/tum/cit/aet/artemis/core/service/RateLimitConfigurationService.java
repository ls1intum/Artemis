package de.tum.cit.aet.artemis.core.service;

import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.config.RateLimitingProperties;
import de.tum.cit.aet.artemis.core.security.RateLimitType;

/**
 * Service for managing rate limiting configuration.
 * Provides centralized access to rate limiting settings including enable/disable flags
 * and configurable RPM values for different endpoint types.
 */
@Service
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
        System.out.println(type.name());
        return switch (type) {
            case PUBLIC -> properties.getPublicRpm() != null ? properties.getPublicRpm() : type.getDefaultRpm();
            case LOGIN_RELATED -> properties.getLoginRelatedRpm() != null ? properties.getLoginRelatedRpm() : type.getDefaultRpm();
        };
    }
}
