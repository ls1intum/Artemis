package de.tum.cit.aet.artemis.core.security.annotations;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.servlet.http.HttpServletRequest;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import de.tum.cit.aet.artemis.core.config.RateLimitConfig;
import de.tum.cit.aet.artemis.core.exception.RateLimitExceededException;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.grid.hazelcast.HazelcastProxyManager;

@Aspect
@Component
public class LimitRequestsPerMinuteAspect {

    private final HazelcastProxyManager<String> proxyManager;

    private final Map<Integer, BucketConfiguration> cfgCache = new ConcurrentHashMap<>();

    private final String headerName;

    public LimitRequestsPerMinuteAspect(HazelcastProxyManager<String> proxyManager) {
        this.proxyManager = proxyManager;
        this.headerName = "X-Forwarded-For";
    }

    @Around("@annotation(LimitRequestsPerMinute) || @within(LimitRequestsPerMinute)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        Method method = ((MethodSignature) pjp.getSignature()).getMethod();
        LimitRequestsPerMinute ann = method.getAnnotation(LimitRequestsPerMinute.class);
        if (ann == null) {
            ann = method.getDeclaringClass().getAnnotation(LimitRequestsPerMinute.class);
        }
        int rpm = ann.value();

        String clientId = resolveClientId();
        String endpoint = method.getDeclaringClass().getName() + "#" + method.getName();
        String bucketKey = endpoint + "|" + clientId;

        BucketConfiguration cfg = cfgCache.computeIfAbsent(rpm, RateLimitConfig::perMinute);

        Bucket bucket = proxyManager.getProxy(bucketKey, () -> cfg);

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (!probe.isConsumed()) {
            long nanos = probe.getNanosToWaitForRefill();
            long seconds = nanos <= 0 ? 0 : Math.max(1, nanos / 1_000_000_000L);
            throw new RateLimitExceededException(seconds);
        }
        return pjp.proceed();
    }

    private String resolveClientId() {
        HttpServletRequest req = currentRequest();
        if (req == null)
            return "unknown";
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
