package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

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

    private static final Logger log = LoggerFactory.getLogger(InternalAccessConfiguration.class);

    private static final String PROPERTY = "artemis.security.internal.allowed-cidrs";

    private static final List<String> OPEN_RANGES = List.of("0.0.0.0/0", "::/0");

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

    /**
     * Logs a warning when the {@code prod} profile is active and the list of allowed ranges admits every address.
     * Operators narrow the list to the addresses of the components that call the internal endpoints.
     *
     * @param environment the environment to read the property and the active profiles from
     */
    public static void warnIfFullyOpen(Environment environment) {
        if (!environment.acceptsProfiles(Profiles.of(ArtemisConstants.SPRING_PROFILE_PRODUCTION))) {
            return;
        }
        List<String> ranges = Binder.get(environment).bind(PROPERTY, Bindable.listOf(String.class)).orElse(List.of());
        if (ranges.stream().anyMatch(range -> OPEN_RANGES.contains(range.trim()))) {
            log.warn("The property {} admits every address, so internal endpoints are reachable from any network. "
                    + "Restrict it to the addresses of the components that call Artemis, for example {}=10.0.0.0/24.", PROPERTY, PROPERTY + "[0]");
        }
    }
}
