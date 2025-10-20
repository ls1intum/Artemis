package de.tum.cit.aet.artemis.core.service;

import static de.tum.cit.aet.artemis.core.util.HttpRequestUtils.getIpStringFromRequest;
import static tech.jhipster.config.JHipsterConstants.SPRING_PROFILE_TEST;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import de.tum.cit.aet.artemis.core.config.RateLimitConfig;
import de.tum.cit.aet.artemis.core.exception.RateLimitExceededException;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.grid.hazelcast.HazelcastProxyManager;

@Service
public class RateLimitService {

    private static final Logger log = LoggerFactory.getLogger(RateLimitService.class);

    /**
     * Pattern to match and extract IPv4 addresses, removing any port numbers.
     */
    private static final Pattern IPV4_PATTERN = Pattern.compile("^(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})(:\\d+)?$");

    /**
     * Pattern to match and extract IPv6 addresses, removing any port numbers.
     */
    private static final Pattern IPV6_PATTERN = Pattern.compile("^\\[?([0-9a-fA-F:]+)\\]?(:\\d+)?$");

    private final HazelcastProxyManager<String> proxyManager;

    private final Map<Integer, BucketConfiguration> perMinuteCfgCache = new ConcurrentHashMap<>();

    private final Environment env;

    private final RateLimitConfigurationService configurationService;

    public RateLimitService(HazelcastProxyManager<String> proxyManager, Environment env, RateLimitConfigurationService configurationService) {
        this.proxyManager = proxyManager;
        this.env = env;
        this.configurationService = configurationService;
    }

    /**
     * Enforces rate limiting by consuming 1 token from a per-minute bucket.
     * Throws {@link RateLimitExceededException} if the rate limit is exceeded.
     *
     * @param clientId identifier for the client (typically an IP address)
     * @param rpm      requests per minute allowed for this client
     * @throws RateLimitExceededException if the rate limit is exceeded
     */
    public void enforcePerMinute(String clientId, int rpm) {
        // Skip rate limiting if disabled globally
        if (!configurationService.isRateLimitingEnabled()) {
            log.debug("Rate limiting is disabled globally, skipping enforcement for client {} at {} rpm", clientId, rpm);
            return;
        }

        if (env.acceptsProfiles(Profiles.of(SPRING_PROFILE_TEST))) {
            log.debug("Skipping rate limit enforcement for client {} at {} rpm in test profile", clientId, rpm);
            return;
        }

        Bucket bucket = getOrCreatePerMinuteBucket(clientId, rpm);
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (!probe.isConsumed()) {
            long seconds = Math.max(1, probe.getNanosToWaitForRefill() / 1_000_000_000L);
            log.warn("Rate limit exceeded for client {} at {} rpm, retry after {} seconds", clientId, rpm, seconds);
            throw new RateLimitExceededException(seconds);
        }

        log.debug("Rate limit check passed for client {} at {} rpm, remaining tokens: {}", clientId, rpm, probe.getRemainingTokens());
    }

    private Bucket getOrCreatePerMinuteBucket(String clientId, int rpm) {
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
    public String resolveClientId() {
        return getIpStringFromRequest(currentRequest());
    }

    private HttpServletRequest currentRequest() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs == null)
            return null;
        return (HttpServletRequest) attrs.resolveReference(RequestAttributes.REFERENCE_REQUEST);
    }
}
