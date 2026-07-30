package de.tum.cit.aet.artemis.core.config.cache;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
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
 * {@link de.tum.cit.aet.artemis.core.service.cache.BlobCacheEvictionService} broadcasts evictions over a distributed topic for exactly that reason. Entries also
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
     * Values above this size are not worth caching: they would evict a large share of the cache for a single entry.
     */
    private static final int MAXIMUM_CACHED_VALUE_BYTES = 32 * 1024 * 1024;

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
        cacheManager.setCaffeine(
                Caffeine.newBuilder().maximumWeight(maximumSizeBytes).weigher(BlobCacheConfiguration::weigh).expireAfterWrite(timeToLiveSeconds, TimeUnit.SECONDS).recordStats());
        return cacheManager;
    }

    /**
     * Estimates the memory a cached value occupies, so the cache can be bounded in bytes.
     *
     * @param key   the cache key
     * @param value the cached value
     * @return the estimated weight in bytes, capped so a single oversized entry cannot dominate the cache
     */
    private static int weigh(Object key, Object value) {
        int weight = switch (value) {
            case byte[] bytes -> bytes.length;
            case String text -> text.length() * 2;
            // Unknown value shapes get a nominal weight; the blob caches only ever hold byte[] and String.
            case null, default -> 1024;
        };
        return Math.min(weight, MAXIMUM_CACHED_VALUE_BYTES);
    }

    /**
     * @param cache the cache to test
     * @return true if the cache is served by this per-node cache manager
     */
    public static boolean isBlobCache(Cache cache) {
        return cache != null && BLOB_CACHE_NAMES.contains(cache.getName());
    }
}
