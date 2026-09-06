package de.tum.cit.aet.artemis.core.config.cache;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;

import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * Per-node cache for the few caches whose values are large binary or text blobs.
 *
 * <p>
 * <strong>Why these are not distributed.</strong> {@code files} holds whole file contents (lecture attachment PDFs,
 * slides, images) and the PlantUML caches hold rendered diagrams. Keeping them in a shared grid means a network transfer
 * of the whole payload on every read, and on Redis it is worse than that: Redis is single-threaded, so a multi-MB value
 * blocks every other operation, including the CI build queue that shares the instance. A bounded per-node cache is both
 * faster than the current remote lookup and cannot affect other traffic.
 *
 * <p>
 * <strong>Coherence.</strong> Each node keeps its own copy, so eviction has to reach every node.
 * {@link de.tum.cit.aet.artemis.core.service.cache.PerNodeCacheEvictionService} broadcasts evictions over a distributed topic for exactly that reason. Entries also
 * carry a time-to-live, so a missed broadcast self-corrects rather than serving a stale file indefinitely.
 *
 * <p>
 * The bound is a <em>weight</em> in bytes rather than an entry count, because entry counts say nothing about memory when
 * individual values range from a few kilobytes to tens of megabytes.
 */
@Profile(PROFILE_CORE)
@Lazy
@Configuration
public class BlobCacheConfiguration {

    /**
     * Cache names served by this per-node cache manager instead of the distributed one.
     */
    public static final Set<String> BLOB_CACHE_NAMES = Set.of("files", "plantUmlPng", "plantUmlSvg");

    /**
     * Nominal weight for value shapes the blob caches are not expected to hold. They only ever hold {@code byte[]} and {@link String}.
     */
    private static final int UNKNOWN_VALUE_WEIGHT_BYTES = 1024;

    /**
     * @param maximumSizeBytes  total byte budget for all blob caches on this node
     * @param timeToLiveSeconds how long an entry stays valid, bounding staleness if an eviction broadcast is lost
     * @return the per-node cache manager serving {@link #BLOB_CACHE_NAMES}
     */
    @Bean
    public CacheManager blobCacheManager(@Value("${artemis.cache.blob.maximum-size-bytes:268435456}") long maximumSizeBytes,
            @Value("${artemis.cache.hazelcast.time-to-live-seconds:3600}") int timeToLiveSeconds) {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCacheNames(BLOB_CACHE_NAMES);
        // CaffeineCacheManager builds one independent cache per name from this single builder, so the configured budget is the per-cache maximum weight.
        // Handing each cache the full budget would let the documented node total be exceeded by the number of caches.
        long maximumWeightPerCache = Math.max(1, maximumSizeBytes / BLOB_CACHE_NAMES.size());
        cacheManager.setCaffeine(Caffeine.newBuilder().maximumWeight(maximumWeightPerCache).weigher(BlobCacheConfiguration::weigh)
                .expireAfterWrite(timeToLiveSeconds, TimeUnit.SECONDS).recordStats());
        return cacheManager;
    }

    /**
     * Reports the memory a cached value occupies, so the cache can be bounded in bytes.
     * <p>
     * The full retained size is reported rather than a capped value. Under-reporting the weight of a large value would let the cache retain far more than its
     * budget, and Caffeine already handles the oversized case correctly on its own: an entry heavier than the maximum weight of its cache is evicted right away,
     * which is exactly the intended "not worth caching" behaviour for a value that would otherwise displace everything else.
     *
     * @param key   the cache key
     * @param value the cached value
     * @return the weight in bytes
     */
    private static int weigh(Object key, Object value) {
        long weight = switch (value) {
            case byte[] bytes -> bytes.length;
            case String text -> 2L * text.length();
            case null, default -> UNKNOWN_VALUE_WEIGHT_BYTES;
        };
        return (int) Math.min(weight, Integer.MAX_VALUE);
    }
}
