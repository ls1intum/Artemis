package de.tum.cit.aet.artemis.core.config.cache;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.Duration;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;

import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * Per-node cache for the title lookups.
 *
 * <p>
 * <strong>Why these are not distributed.</strong> Each of these caches answers with one column of one row, found by
 * primary key. A distributed cache turns that into a request to another machine, which is not obviously cheaper than
 * asking the database, and on Redis it shares an instance with the build queue. Holding the answer in this process
 * removes the hop entirely: a hit costs a map lookup rather than a network round trip and a deserialization.
 *
 * <p>
 * <strong>Coherence.</strong> Each node keeps its own copy, so a rename has to reach every node.
 * {@link de.tum.cit.aet.artemis.core.service.cache.PerNodeCacheEvictionService} broadcasts the eviction over a
 * distributed topic for exactly that reason, and
 * {@link de.tum.cit.aet.artemis.core.service.TitleCacheEvictionService} is what calls it, from the Hibernate flush that
 * wrote the new title.
 *
 * <p>
 * <strong>Why the lifetime is short.</strong> A broadcast can be missed, by a node that is still joining for instance,
 * and a stale title is visible to a reader in a way a stale rendered diagram is not. Five minutes bounds that without
 * costing much: recomputing a title is a single indexed read, so an expiry is close to free, and the cache exists to
 * collapse repeated reads within a request rather than to hold an answer for hours.
 */
@Profile(PROFILE_CORE)
@Lazy
@Configuration
public class TitleCacheConfiguration {

    /**
     * Cache names served by this per-node cache manager instead of the distributed one.
     *
     * <p>
     * Named by literal rather than by module constants, because {@code core} may not depend on a feature module.
     */
    public static final Set<String> TITLE_CACHE_NAMES = Set.of("courseTitle", "exerciseTitle", "examTitle", "lectureTitle", "competencyTitle", "tutorialGroupTitle", "diagramTitle",
            "organizationTitle");

    /**
     * Entries per title cache. A title is a short string, so the bound is an entry count rather than a byte weight, and
     * it is generous: the working set is the courses and exercises a node is currently serving.
     */
    private static final long MAXIMUM_ENTRIES_PER_CACHE = 10_000;

    /**
     * @param timeToLiveSeconds how long a title stays valid, bounding staleness if an eviction broadcast is lost
     * @return the per-node cache manager serving {@link #TITLE_CACHE_NAMES}
     */
    @Bean("titleCacheManager")
    public CacheManager titleCacheManager(@Value("${artemis.cache.title.time-to-live-seconds:300}") int timeToLiveSeconds) {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCacheNames(TITLE_CACHE_NAMES);
        // Zero is a way to turn the caches off without removing them, since an entry then expires as it is written.
        cacheManager.setCaffeine(Caffeine.newBuilder().maximumSize(MAXIMUM_ENTRIES_PER_CACHE).expireAfterWrite(Duration.ofSeconds(timeToLiveSeconds)));
        return cacheManager;
    }
}
