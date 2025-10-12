package de.tum.cit.aet.artemis.core.security;

/**
 * Enum defining different rate limit types with their default requests per minute (RPM) values.
 * These values can be overridden through application configuration.
 */
public enum RateLimitType {

    /**
     * Rate limit for public endpoints that don't require authentication.
     * Default: 5 requests per minute per client.
     */
    PUBLIC(5),

    /**
     * Rate limit for login-related endpoints (authentication, password reset, registration).
     * Default: 30 requests per minute per client.
     */
    LOGIN_RELATED(30);

    private final int defaultRpm;

    RateLimitType(int defaultRpm) {
        this.defaultRpm = defaultRpm;
    }

    /**
     * Gets the default requests per minute for this rate limit type.
     *
     * @return the default RPM value
     */
    public int getDefaultRpm() {
        return defaultRpm;
    }
}
