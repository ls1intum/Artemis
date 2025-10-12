package de.tum.cit.aet.artemis.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for rate limiting functionality.
 *
 * <p>
 * This class binds the artemis.rate-limiting.* properties from application configuration
 * and provides type-safe access to rate limiting settings.
 * </p>
 */
@Component
@ConfigurationProperties(prefix = "artemis.rate-limiting")
public class RateLimitingProperties {

    /**
     * Whether rate limiting is enabled globally.
     * Default: false (disabled)
     */
    private boolean enabled = false;

    /**
     * Requests per minute for public endpoints.
     * If not specified, uses the default from RateLimitType.PUBLIC.
     */
    private Integer publicRpm;

    /**
     * Requests per minute for login-related endpoints.
     * If not specified, uses the default from RateLimitType.LOGIN_RELATED.
     */
    private Integer loginRelatedRpm;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Integer getPublicRpm() {
        return publicRpm;
    }

    public void setPublicRpm(Integer publicRpm) {
        this.publicRpm = publicRpm;
    }

    public Integer getLoginRelatedRpm() {
        return loginRelatedRpm;
    }

    public void setLoginRelatedRpm(Integer loginRelatedRpm) {
        this.loginRelatedRpm = loginRelatedRpm;
    }
}
