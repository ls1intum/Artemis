package de.tum.cit.aet.artemis.core.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.util.HttpRequestUtils.getIpStringFromRequest;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import de.tum.cit.aet.artemis.core.config.RateLimitConfig;
import de.tum.cit.aet.artemis.core.exception.RateLimitExceededException;
import de.tum.cit.aet.artemis.core.security.RateLimitType;
import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressString;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.grid.hazelcast.HazelcastProxyManager;

@Profile(PROFILE_CORE)
@Service
@Lazy
public class RateLimitService {

    private static final Logger log = LoggerFactory.getLogger(RateLimitService.class);

    // Bucket4J proxy that connects rate-limit buckets to Hazelcast for cluster-wide synchronization
    private final HazelcastProxyManager<String> proxyManager;

    private final Map<Integer, BucketConfiguration> perMinuteCfgCache = new ConcurrentHashMap<>();

    private final RateLimitConfigurationService configurationService;

    public RateLimitService(HazelcastProxyManager<String> proxyManager, RateLimitConfigurationService configurationService) {
        this.proxyManager = proxyManager;
        this.configurationService = configurationService;
    }

    /**
     * Enforces rate limiting by consuming 1 token from a per-minute bucket.
     * Throws {@link RateLimitExceededException} if the rate limit is exceeded.
     *
     * @param clientId identifier for the client (typically an IP address)
     * @param rpmType  the rate limit type to determine the RPM configuration
     * @throws RateLimitExceededException if the rate limit is exceeded
     */
    public void enforcePerMinute(IPAddress clientId, RateLimitType rpmType) {
        // Skip rate limiting if disabled globally
        if (!configurationService.isRateLimitingEnabled()) {
            log.debug("Rate limiting is disabled globally, skipping enforcement for client {} at {}", clientId, rpmType.name());
            return;
        }

        Bucket bucket = getOrCreatePerMinuteBucket(clientId, configurationService.getEffectiveRpm(rpmType));
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (!probe.isConsumed()) {
            long seconds = Math.max(1, probe.getNanosToWaitForRefill() / 1_000_000_000L);
            log.warn("Rate limit exceeded for client {} at {}, retry after {} seconds", clientId, rpmType.name(), seconds);
            throw new RateLimitExceededException(seconds);
        }

        log.debug("Rate limit check passed for client {} at {}, remaining tokens: {}", clientId, rpmType.name(), probe.getRemainingTokens());
    }

    private Bucket getOrCreatePerMinuteBucket(IPAddress clientId, int rpm) {
        BucketConfiguration cfg = perMinuteCfgCache.computeIfAbsent(rpm, RateLimitConfig::perMinute);
        return proxyManager.getProxy("rpm=" + rpm + "#" + clientId, () -> cfg);
    }

    /**
     * Resolves the client identifier from the current HTTP request.
     *
     * <p>
     * IP Resolution Strategy:
     * </p>
     * <ol>
     * <li>Checks headers (for requests through proxies)</li>
     * <li>Falls back to direct remote address</li>
     * <li>Cleans up IP by removing ports and normalizing format</li>
     * </ol>
     *
     * @return the cleaned client IP address
     */
    public IPAddress resolveClientId() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            log.warn("Unable to resolve HTTP request context for rate limiting");
            return null;
        }

        final String ipString = getIpStringFromRequest(request);
        IPAddress address = new IPAddressString(ipString).getAddress();

        if (address == null) {
            log.warn("Failed to parse IP address '{}' for rate limiting", ipString);
        }

        return address;
    }

    private HttpServletRequest currentRequest() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return null;
        }
        return (HttpServletRequest) attrs.resolveReference(RequestAttributes.REFERENCE_REQUEST);
    }
}
