package de.tum.cit.aet.artemis.core.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Lazy;

/**
 * Configuration properties for the PROXY protocol on the git SSH listener.
 * <p>
 * Bound to the {@code artemis.version-control.ssh-proxy-protocol} prefix.
 * <p>
 * A load balancer that forwards the SSH port at the TCP level hides the real client: without PROXY protocol every
 * connection appears to come from the balancer, which makes per-client rate limiting share a single bucket and makes
 * any origin check on a build agent meaningless. Enabling {@code proxy_protocol on} in the nginx {@code stream} block
 * and listing the balancer here restores the real client address.
 *
 * @see de.tum.cit.aet.artemis.localvc.service.ssh.ProxyProtocolAcceptor
 */
@Lazy
@ConfigurationProperties(prefix = "artemis.version-control.ssh-proxy-protocol")
public class SshProxyProtocolConfiguration {

    /**
     * Networks that must announce the real client with a PROXY protocol header, i.e. the reverse proxies that forward
     * this installation's SSH port.
     * <p>
     * Deliberately keyed on the source address rather than on the presence of a header. Accepting a header from
     * whoever sends one would let anybody who can reach the SSH port claim an arbitrary client address and walk
     * straight through an origin check. A connection from a listed address must therefore begin with a valid header
     * and is rejected otherwise, while a connection from anywhere else is treated as ordinary SSH and keeps its socket
     * peer as the client address.
     * <p>
     * That is also what keeps this safe to enable by default for the setups this repository ships: direct connections
     * from developers, tests and build agents on the compose network are unaffected, because they do not come from a
     * listed address.
     * <p>
     * An empty list disables PROXY protocol entirely.
     */
    private List<String> trustedSources = List.of();

    public List<String> getTrustedSources() {
        return trustedSources;
    }

    public void setTrustedSources(List<String> trustedSources) {
        this.trustedSources = trustedSources == null ? List.of() : trustedSources;
    }
}
