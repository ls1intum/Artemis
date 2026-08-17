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
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.core.util.IpRangeSet;

/**
 * Answers the two network questions the build agent authorization paths ask: whether an address is inside the
 * configured build agent networks, and whether an address is a reverse proxy this installation operates.
 * <p>
 * The configured ranges are parsed once here rather than per request, by {@link IpRangeSet}, which rejects a malformed
 * value at startup with the offending value named rather than letting it silently never match - on an allowlist that
 * would mean refusing every build agent - and which treats an address of another family as a plain non-match rather
 * than an error.
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

    private final IpRangeSet allowedRanges;

    private final IpRangeSet trustedProxies;

    public BuildAgentNetworkPolicy(BuildAgentNetworkConfiguration configuration) {
        this.allowedRanges = IpRangeSet.parse(configuration.getAllowedRanges(), ALLOWED_RANGES_PROPERTY);
        this.trustedProxies = IpRangeSet.parse(configuration.getTrustedProxies(), TRUSTED_PROXIES_PROPERTY);
    }

    /**
     * Logs what this node will enforce, so a deployment that expected the allowlist to be active can tell from the
     * startup log that it is not, rather than from the absence of a rejection much later.
     */
    @PostConstruct
    public void logConfiguredPolicy() {
        if (allowedRanges.isEmpty()) {
            log.info("No build agent networks are configured ({} is empty), so build agents may clone from any address. Configure it to bound which hosts may act as build agents.",
                    ALLOWED_RANGES_PROPERTY);
        }
        else {
            log.info("Build agents may only clone from {}", allowedRanges);
        }
        if (!trustedProxies.isEmpty()) {
            log.info("X-Forwarded-For is believed for HTTP git requests arriving from {}. Note that Tomcat's server.tomcat.remoteip.internal-proxies is applied first and "
                    + "defaults to the private ranges, so the effective set is the union of the two.", trustedProxies);
        }
    }

    /**
     * @return whether any build agent network is configured. When none is, {@link #isWithinAllowedRanges} always
     *         returns {@code true}.
     */
    public boolean isAllowlistConfigured() {
        return !allowedRanges.isEmpty();
    }

    /**
     * Checks an address against the configured build agent networks.
     *
     * @param ipAddress the address to check, may be null
     * @return {@code true} if no networks are configured, or the address lies inside one of them. {@code false} for a
     *         null or unparsable address whenever networks are configured.
     */
    public boolean isWithinAllowedRanges(String ipAddress) {
        if (allowedRanges.isEmpty()) {
            // An empty allowlist means no restriction, never "deny all" - see BuildAgentNetworkConfiguration.
            return true;
        }
        return allowedRanges.contains(ipAddress);
    }

    /**
     * Checks whether an address is a reverse proxy operated by this installation, and therefore whether the
     * {@code X-Forwarded-For} header of a request arriving from it may be believed.
     *
     * @param ipAddress the address to check, may be null
     * @return whether the address is a configured trusted proxy
     */
    public boolean isTrustedProxy(String ipAddress) {
        return trustedProxies.contains(ipAddress);
    }

    /**
     * @return the configured build agent networks, for display in the admin UI
     */
    public List<String> getAllowedRanges() {
        return allowedRanges.getConfiguredRanges();
    }
}
