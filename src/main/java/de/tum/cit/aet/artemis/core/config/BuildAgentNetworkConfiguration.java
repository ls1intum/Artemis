package de.tum.cit.aet.artemis.core.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Lazy;

/**
 * Configuration properties restricting which networks build agents may clone repositories from.
 * <p>
 * Bound to the {@code artemis.continuous-integration.build-agent-network} prefix. Both lists accept CIDR blocks and
 * single addresses, IPv4 and IPv6, in the syntax of {@link org.springframework.security.web.util.matcher.IpAddressMatcher}.
 *
 * @see BuildAgentNetworkPolicy
 */
@Lazy
@ConfigurationProperties(prefix = "artemis.continuous-integration.build-agent-network")
public class BuildAgentNetworkConfiguration {

    /**
     * Networks build agents are permitted to connect from.
     * <p>
     * An empty list means no restriction, which is deliberate and must not be read as "deny all": the property is
     * absent in every existing installation, and treating that as a deny would stop all builds on upgrade. Configure
     * it to bound which hosts may register as build agents at all.
     * <p>
     * Example values: {@code "10.0.0.0/8"}, {@code "192.168.1.7"}, {@code "2001:db8::/32"}
     */
    private List<String> allowedRanges = List.of();

    /**
     * Networks whose {@code X-Forwarded-For} header may be believed when resolving the client of an HTTP git request.
     * <p>
     * Anyone can set that header, so it is only consulted when the request's actual TCP peer is one of these
     * addresses, i.e. a reverse proxy this installation operates. An empty list means the header is never trusted and
     * the TCP peer is always used.
     */
    private List<String> trustedProxies = List.of();

    public List<String> getAllowedRanges() {
        return allowedRanges;
    }

    public void setAllowedRanges(List<String> allowedRanges) {
        this.allowedRanges = allowedRanges == null ? List.of() : allowedRanges;
    }

    public List<String> getTrustedProxies() {
        return trustedProxies;
    }

    public void setTrustedProxies(List<String> trustedProxies) {
        this.trustedProxies = trustedProxies == null ? List.of() : trustedProxies;
    }
}
