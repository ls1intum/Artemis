package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;

/**
 * Configuration properties for rate limiting functionality.
 *
 * <p>
 * This class binds the artemis.rate-limiting.* properties from application configuration
 * and provides type-safe access to rate limiting settings.
 * </p>
 */
@Profile(PROFILE_CORE)
@Configuration
@Lazy
@ConfigurationProperties(prefix = "artemis.rate-limiting")
public class RateLimitingProperties {

    /**
     * Whether rate limiting is enabled globally.
     * Default: false (disabled)
     */
    private boolean enabled = false;

    /**
     * Requests per minute for public endpoints.
     * If not specified, uses the default from {@link de.tum.cit.aet.artemis.core.security.RateLimitType#ACCOUNT_MANAGEMENT}.
     */
    private Integer accountManagementRequestsPerMinute;

    /**
     * Requests per minute for login-related endpoints.
     * If not specified, uses the default from {@link de.tum.cit.aet.artemis.core.security.RateLimitType#AUTHENTICATION}.
     */
    private Integer authenticationRequestsPerMinute;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Integer getAccountManagementRequestsPerMinute() {
        return accountManagementRequestsPerMinute;
    }

    public void setAccountManagementRequestsPerMinute(Integer accountManagementRequestsPerMinute) {
        this.accountManagementRequestsPerMinute = accountManagementRequestsPerMinute;
    }

    public Integer getAuthenticationRequestsPerMinute() {
        return authenticationRequestsPerMinute;
    }

    public void setAuthenticationRequestsPerMinute(Integer authenticationRequestsPerMinute) {
        this.authenticationRequestsPerMinute = authenticationRequestsPerMinute;
    }
}
