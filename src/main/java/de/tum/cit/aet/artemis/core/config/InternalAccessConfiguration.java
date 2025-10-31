package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;

import jakarta.annotation.Nullable;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;

/**
 * Configuration properties for restricting access to endpoints annotated with
 * {@code @Internal}.
 * <p>
 * This configuration is bound to the {@code artemis.security.internal} prefix
 * in application properties.
 * It defines which IP address ranges are allowed to access internal endpoints.
 */
@Profile(PROFILE_CORE)
@Lazy
@ConfigurationProperties(prefix = "artemis.security.internal")
public class InternalAccessConfiguration {

    /**
     * List of CIDR blocks (IPv4/IPv6) allowed to access @Internal endpoints.
     * <p>
     * If not configured or empty, all internal endpoints will not be accessible from
     * any IP address.
     * Example values: "10.0.0.0/8", "192.168.1.0/24", "2001:db8::/32"
     */
    @Nullable
    private List<String> allowedCidrs;

    /**
     * Gets the list of allowed CIDR blocks.
     *
     * @return the list of CIDR blocks, or null if not configured
     */
    @Nullable
    public List<String> getAllowedCidrs() {
        return allowedCidrs;
    }

    /**
     * Sets the list of allowed CIDR blocks.
     *
     * @param allowedCidrs the list of CIDR blocks to allow
     */
    public void setAllowedCidrs(@Nullable List<String> allowedCidrs) {
        this.allowedCidrs = allowedCidrs;
    }
}
