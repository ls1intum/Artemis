package de.tum.cit.aet.artemis.admin.service;

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
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggleService;
import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressString;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.proxy.ProxyManager;

@Profile(PROFILE_CORE)
@Service
@Lazy
public class RateLimitService {

    private static final Logger log = LoggerFactory.getLogger(RateLimitService.class);

    // Bucket4J proxy that connects rate-limit buckets to Hazelcast for cluster-wide synchronization
    private final ProxyManager<String> proxyManager;

    private final Map<Integer, BucketConfiguration> perMinuteCfgCache = new ConcurrentHashMap<>();

    private final RateLimitConfigurationService configurationService;

    private final FeatureToggleService featureToggleService;

    public RateLimitService(ProxyManager<String> proxyManager, RateLimitConfigurationService configurationService, FeatureToggleService featureToggleService) {
        this.proxyManager = proxyManager;
        this.configurationService = configurationService;
        this.featureToggleService = featureToggleService;
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
        // Skip rate limiting if disabled globally or disabled via feature toggle
        if (!configurationService.isRateLimitingEnabled() || !featureToggleService.isFeatureEnabled(Feature.RateLimit)) {
            log.debug("Rate limiting is disabled globally, skipping enforcement for client {} at {}", clientId, rpmType.name());
            return;
        }

        // An exempt address consumes no tokens at all, rather than getting a larger bucket: a load
        // generator drives thousands of requests from one address, and any finite bucket would still
        // throttle it partway through a run and quietly turn the measurement into a measurement of the
        // limiter.
        if (configurationService.isExempt(clientId)) {
            log.debug("Client {} is exempt from rate limiting, skipping enforcement at {}", clientId, rpmType.name());
            return;
        }

        Bucket bucket = getOrCreatePerMinuteBucket(clientId, rpmType, configurationService.getEffectiveRpm(rpmType));
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (!probe.isConsumed()) {
            long seconds = Math.max(1, probe.getNanosToWaitForRefill() / 1_000_000_000L);
            log.warn("Rate limit exceeded for client {} at {}, retry after {} seconds", clientId, rpmType.name(), seconds);
            throw new RateLimitExceededException(seconds);
        }

        log.debug("Rate limit check passed for client {} at {}, remaining tokens: {}", clientId, rpmType.name(), probe.getRemainingTokens());
    }

    /**
     * Checks whether a client still has budget for a rate limit type without spending any of it.
     * <p>
     * Pairs with {@link #consumePerMinute} for a check whose cost falls on failure rather than on use. The build agent
     * clone-token check works that way: it runs ahead of the rate limiter for every git read carrying a Basic header,
     * and the work it protects - a scan of the distributed processing jobs - has to be refused once a caller has spent
     * its budget, but must not charge the agents whose builds depend on it. Spending on decline instead keeps the limit
     * a bound on guessing rather than a cap on build throughput.
     *
     * @param clientId identifier for the client (typically an IP address)
     * @param rpmType  the rate limit type to determine the RPM configuration
     * @return whether the client may still be served, {@code true} when rate limiting is off or the client is exempt
     */
    public boolean hasRemainingBudget(IPAddress clientId, RateLimitType rpmType) {
        if (!configurationService.isRateLimitingEnabled() || !featureToggleService.isFeatureEnabled(Feature.RateLimit) || configurationService.isExempt(clientId)) {
            return true;
        }
        return getOrCreatePerMinuteBucket(clientId, rpmType, configurationService.getEffectiveRpm(rpmType)).getAvailableTokens() > 0;
    }

    /**
     * Spends one token of a client's budget for a rate limit type, without throwing when it is already empty.
     * <p>
     * The counterpart of {@link #hasRemainingBudget}: the caller has already decided what to do, and only records that
     * this client used up an attempt.
     *
     * @param clientId identifier for the client (typically an IP address)
     * @param rpmType  the rate limit type to determine the RPM configuration
     */
    public void consumePerMinute(IPAddress clientId, RateLimitType rpmType) {
        if (!configurationService.isRateLimitingEnabled() || !featureToggleService.isFeatureEnabled(Feature.RateLimit) || configurationService.isExempt(clientId)) {
            return;
        }
        getOrCreatePerMinuteBucket(clientId, rpmType, configurationService.getEffectiveRpm(rpmType)).tryConsume(1);
    }

    private Bucket getOrCreatePerMinuteBucket(IPAddress clientId, RateLimitType rpmType, int rpm) {
        BucketConfiguration cfg = perMinuteCfgCache.computeIfAbsent(rpm, RateLimitConfig::perMinute);
        // Include the rate-limit type in the bucket key so two types with the same RPM do not share a bucket.
        return proxyManager.getProxy("type=" + rpmType.name() + "#rpm=" + rpm + "#" + clientId, () -> cfg);
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
