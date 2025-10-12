package de.tum.cit.aet.artemis.core.service;

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
     * Resolves the client identifier from the current HTTP request with comprehensive IP cleanup.
     *
     * <p>
     * IP Resolution Strategy:
     * </p>
     * <ol>
     * <li>Checks X-Forwarded-For header (for requests through proxies)</li>
     * <li>Falls back to direct remote address</li>
     * <li>Cleans up IP by removing ports and normalizing format</li>
     * </ol>
     *
     * @return the cleaned client IP address, or "unknown" if unavailable
     */
    public String resolveClientId() {
        HttpServletRequest req = currentRequest();
        if (req == null) {
            log.debug("No HTTP request context available, using 'unknown' as client ID");
            return "unknown";
        }

        // Try X-Forwarded-For header first
        String forwardedFor = req.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.trim().isEmpty()) {
            String firstIp = forwardedFor.split(",")[0].trim();
            String cleanedIp = cleanupIpAddress(firstIp);
            log.debug("Resolved client ID from X-Forwarded-For: {} -> {}", firstIp, cleanedIp);
            return cleanedIp;
        }

        // Fallback to direct remote address
        String remoteAddr = req.getRemoteAddr();
        if (remoteAddr != null) {
            String cleanedIp = cleanupIpAddress(remoteAddr);
            log.debug("Resolved client ID from remote address: {} -> {}", remoteAddr, cleanedIp);
            return cleanedIp;
        }

        log.debug("No IP address available in request, using 'unknown' as client ID");
        return "unknown";
    }

    /**
     * Cleans up an IP address by removing port numbers and normalizing format.
     *
     * <p>
     * Handles various formats:
     * </p>
     * <ul>
     * <li>IPv4 with port: "192.168.1.1:8080" → "192.168.1.1"</li>
     * <li>IPv6 with port: "[::1]:8080" → "::1"</li>
     * </ul>
     *
     * @param rawIp the raw IP address that may include port numbers
     * @return the cleaned IP address without port numbers
     */
    private String cleanupIpAddress(String rawIp) {
        if (rawIp == null || rawIp.trim().isEmpty()) {
            return "unknown";
        }

        String trimmed = rawIp.trim();

        // Try IPv4 pattern
        var ipv4Matcher = IPV4_PATTERN.matcher(trimmed);
        if (ipv4Matcher.matches()) {
            return ipv4Matcher.group(1);
        }

        // Try IPv6 pattern
        var ipv6Matcher = IPV6_PATTERN.matcher(trimmed);
        if (ipv6Matcher.matches()) {
            return ipv6Matcher.group(1);
        }

        // Return trimmed original if no pattern matches
        log.debug("Could not parse IP address format: {}, using as-is", trimmed);
        return trimmed;
    }

    private HttpServletRequest currentRequest() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs == null)
            return null;
        return (HttpServletRequest) attrs.resolveReference(RequestAttributes.REFERENCE_REQUEST);
    }
}
