package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;

import jakarta.annotation.PostConstruct;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.core.util.IpRangeSet;

/**
 * Decides which peer addresses may read the Prometheus metrics endpoint ({@code /management/prometheus}).
 * <p>
 * The addresses come from {@code spring.prometheus.monitoringIp}, a comma-separated list of single addresses and CIDR
 * blocks such as {@code 10.42.0.0/16}. A block matters on Kubernetes, where the address of a Prometheus pod is not
 * stable. A value that is not an address or a block stops the start, with the property named, because an allowlist that
 * silently never matches would deny the scrape without any trace of why.
 * <p>
 * Blank entries are dropped before parsing instead of rejected, which keeps what an empty value always meant: nobody
 * may scrape. {@link IpRangeSet} would refuse them, since its parser reads an empty string as the loopback address.
 * The default is the loopback address, as before.
 * <p>
 * The address checked is the peer of the connection, not a forwarded one, because any client can send a
 * {@code X-Forwarded-For} header.
 */
@Component
@Profile(PROFILE_CORE)
@Lazy(false)
public class MonitoringAccessPolicy {

    private static final Logger log = LoggerFactory.getLogger(MonitoringAccessPolicy.class);

    private static final String PROPERTY = "spring.prometheus.monitoringIp";

    private final IpRangeSet allowedAddresses;

    public MonitoringAccessPolicy(@Value("#{'${spring.prometheus.monitoringIp:127.0.0.1}'.split(',')}") List<String> configuredAddresses) {
        this.allowedAddresses = IpRangeSet.parse(configuredAddresses.stream().filter(address -> !address.isBlank()).toList(), PROPERTY);
    }

    /**
     * Logs what is allowed, so that an operator who expects Prometheus to be able to scrape can tell from the startup
     * log that no address is allowed, instead of from a denied scrape later.
     */
    @PostConstruct
    public void logConfiguredPolicy() {
        if (allowedAddresses.isEmpty()) {
            log.info("{} is empty, so no address may read /management/prometheus.", PROPERTY);
        }
        else {
            log.info("/management/prometheus may be read from {}", allowedAddresses);
        }
    }

    /**
     * Checks whether a peer may read the metrics.
     *
     * @param peerAddress the address of the connection, may be null or not an address
     * @return whether one of the configured addresses or blocks contains it
     */
    public boolean isAllowed(@Nullable String peerAddress) {
        return allowedAddresses.contains(peerAddress);
    }
}
