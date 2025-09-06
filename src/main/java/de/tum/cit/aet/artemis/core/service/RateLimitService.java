package de.tum.cit.aet.artemis.core.service;

import static tech.jhipster.config.JHipsterConstants.SPRING_PROFILE_TEST;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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

    private final HazelcastProxyManager<String> proxyManager;

    private final Map<Integer, BucketConfiguration> perMinuteCfgCache = new ConcurrentHashMap<>();

    private final Environment env;

    public RateLimitService(HazelcastProxyManager<String> proxyManager, Environment env) {
        this.proxyManager = proxyManager;
        this.env = env;
    }

    /**
     * Consume 1 token from a per-minute bucket. Throws on limit exceed.
     *
     * @param clientId identifier for the client that is being rate-limited (e.g. IP address)
     * @param rpm      requests per minute
     */
    public void enforcePerMinute(String clientId, int rpm) {
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
    }

    private Bucket getOrCreatePerMinuteBucket(String clientId, int rpm) {
        BucketConfiguration cfg = perMinuteCfgCache.computeIfAbsent(rpm, RateLimitConfig::perMinute);
        return proxyManager.getProxy("rpm=" + rpm + "#" + clientId, () -> cfg);
    }

    public String resolveClientId() {
        HttpServletRequest req = currentRequest();
        if (req == null)
            return "unknown";
        String headerName = "X-Forwarded-For";
        String raw = req.getHeader(headerName);
        if (raw == null || raw.isBlank()) {
            return req.getRemoteAddr() != null ? req.getRemoteAddr() : "unknown";
        }
        int comma = raw.indexOf(',');
        return comma > 0 ? raw.substring(0, comma).trim() : raw.trim();
    }

    private HttpServletRequest currentRequest() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs == null)
            return null;
        return (HttpServletRequest) attrs.resolveReference(RequestAttributes.REFERENCE_REQUEST);
    }
}
