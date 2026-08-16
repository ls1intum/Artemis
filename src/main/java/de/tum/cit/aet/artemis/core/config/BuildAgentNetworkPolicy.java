package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALCI;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALVC;

import java.util.List;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;

/**
 * Answers the two network questions the build agent authorization paths ask: whether an address is inside the
 * configured build agent networks, and whether an address is a reverse proxy this installation operates.
 * <p>
 * The configured ranges are parsed once here rather than per request. {@link IpAddressMatcher} rejects a malformed
 * value in its constructor, so an unusable configuration fails startup with the offending value named instead of
 * silently never matching, which on an allowlist would mean refusing every build agent.
 * <p>
 * This deliberately does not use {@link de.tum.cit.aet.artemis.core.util.HttpRequestUtils#getIpStringFromRequest} to
 * obtain the address it is asked about. That helper returns the first {@code X-Forwarded-For} value whenever the
 * header is present, and any client can set that header, so it cannot be the basis of an allowlist. Callers resolve
 * the peer with {@link de.tum.cit.aet.artemis.core.util.HttpRequestUtils#getPeerIpString} instead.
 *
 * @see BuildAgentNetworkConfiguration
 */
@Component
// Both roles need it: local VC authorizes git requests with it, local CI marks agents outside the allowlist
@Profile({ PROFILE_LOCALVC, PROFILE_LOCALCI })
@Lazy(false)
@EnableConfigurationProperties(BuildAgentNetworkConfiguration.class)
public class BuildAgentNetworkPolicy {

    private static final Logger log = LoggerFactory.getLogger(BuildAgentNetworkPolicy.class);

    private static final String ALLOWED_RANGES_PROPERTY = "artemis.continuous-integration.build-agent-network.allowed-ranges";

    private static final String TRUSTED_PROXIES_PROPERTY = "artemis.continuous-integration.build-agent-network.trusted-proxies";

    private final List<String> allowedRanges;

    private final List<IpAddressMatcher> allowedRangeMatchers;

    private final List<String> trustedProxies;

    private final List<IpAddressMatcher> trustedProxyMatchers;

    public BuildAgentNetworkPolicy(BuildAgentNetworkConfiguration configuration) {
        this.allowedRanges = List.copyOf(configuration.getAllowedRanges());
        this.allowedRangeMatchers = parse(this.allowedRanges, ALLOWED_RANGES_PROPERTY);
        this.trustedProxies = List.copyOf(configuration.getTrustedProxies());
        this.trustedProxyMatchers = parse(this.trustedProxies, TRUSTED_PROXIES_PROPERTY);
    }

    private static List<IpAddressMatcher> parse(List<String> ranges, String propertyName) {
        return ranges.stream().map(range -> {
            try {
                return new IpAddressMatcher(range);
            }
            catch (IllegalArgumentException e) {
                throw new IllegalStateException(
                        "Cannot parse '" + range + "' in " + propertyName + " as an IP address or CIDR block. Use a single address such as 192.168.1.7 or a block such as "
                                + "10.0.0.0/8 or 2001:db8::/32. Refusing to start rather than silently never matching, which on an allowlist would refuse every build agent.",
                        e);
            }
        }).toList();
    }

    /**
     * Logs what this node will enforce, so a deployment that expected the allowlist to be active can tell from the
     * startup log that it is not, rather than from the absence of a rejection much later.
     */
    @PostConstruct
    public void logConfiguredPolicy() {
        if (allowedRangeMatchers.isEmpty()) {
            log.info("No build agent networks are configured ({} is empty), so build agents may clone from any address. Configure it to bound which hosts may act as build agents.",
                    ALLOWED_RANGES_PROPERTY);
        }
        else {
            log.info("Build agents may only clone from {}", allowedRanges);
        }
        if (!trustedProxyMatchers.isEmpty()) {
            log.info("X-Forwarded-For is believed for HTTP git requests arriving from {}", trustedProxies);
        }
    }

    /**
     * @return whether any build agent network is configured. When none is, {@link #isWithinAllowedRanges} always
     *         returns {@code true}.
     */
    public boolean isAllowlistConfigured() {
        return !allowedRangeMatchers.isEmpty();
    }

    /**
     * Checks an address against the configured build agent networks.
     *
     * @param ipAddress the address to check, may be null
     * @return {@code true} if no networks are configured, or the address lies inside one of them. {@code false} for a
     *         null or unparsable address whenever networks are configured.
     */
    public boolean isWithinAllowedRanges(String ipAddress) {
        if (allowedRangeMatchers.isEmpty()) {
            // An empty allowlist means no restriction, never "deny all" - see BuildAgentNetworkConfiguration.
            return true;
        }
        return matchesAny(allowedRangeMatchers, ipAddress);
    }

    /**
     * Checks whether an address is a reverse proxy operated by this installation, and therefore whether the
     * {@code X-Forwarded-For} header of a request arriving from it may be believed.
     *
     * @param ipAddress the address to check, may be null
     * @return whether the address is a configured trusted proxy
     */
    public boolean isTrustedProxy(String ipAddress) {
        return matchesAny(trustedProxyMatchers, ipAddress);
    }

    private static boolean matchesAny(List<IpAddressMatcher> matchers, String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank()) {
            return false;
        }
        for (IpAddressMatcher matcher : matchers) {
            try {
                if (matcher.matches(ipAddress)) {
                    return true;
                }
            }
            catch (IllegalArgumentException e) {
                // An unparsable address cannot match anything. Keep checking the remaining matchers rather than
                // letting a malformed value abort the whole decision.
                log.debug("Cannot match '{}' against a configured range", ipAddress, e);
            }
        }
        return false;
    }

    /**
     * @return the configured build agent networks, for display in the admin UI
     */
    public List<String> getAllowedRanges() {
        return allowedRanges;
    }
}
