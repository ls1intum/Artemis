package de.tum.cit.aet.artemis.core.config.cache;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.Duration;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.info.GitProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;

/**
 * Exposes the {@link CacheManager} that {@code @Cacheable} resolves against, and enables Spring's caching support.
 *
 * <p>
 * It routes by cache name: large blob values and the title lookups stay on the node that reads them, and every cache
 * whose entries have to be identical on all nodes stays shared. See {@link BlobCacheConfiguration} and
 * {@link TitleCacheConfiguration} for why those two are local.
 *
 * <p>
 * Caching is enabled here rather than on the Hazelcast configuration, because that one only exists when Hazelcast is the
 * configured provider. Leaving {@code @EnableCaching} there would silently turn every {@code @Cacheable} into a plain
 * method call as soon as a deployment selects Redis.
 */
@Profile(PROFILE_CORE)
@Lazy
@Configuration
@EnableCaching
public class CacheManagerConfiguration {

    /**
     * Cluster-wide caches whose entries expire, with the lifetime each one needs.
     *
     * <p>
     * Everything not listed here is invalidated explicitly by its writer and is created without expiry, which is what
     * the Hazelcast map configuration did for those maps before the cache manager became provider-neutral. The Atlas
     * agent caches hold per-session state that no writer ever cleans up when a user simply abandons a session, so they
     * need a lifetime instead. Two hours matches the {@code createAtlasSessionMapConfig} lifetime they had.
     *
     * <p>
     * Named by literal rather than by the module constants, because {@code core} may not depend on a feature module.
     */
    private static final Duration ATLAS_SESSION_TIME_TO_LIVE = Duration.ofHours(2);

    private static final Map<String, Duration> EXPIRING_CACHES = Map.of("atlas-session-pending-operations", ATLAS_SESSION_TIME_TO_LIVE, "atlas-session-pending-relations",
            ATLAS_SESSION_TIME_TO_LIVE, "atlas-session-exercise-preview", ATLAS_SESSION_TIME_TO_LIVE, "atlas-session-relation-preview", ATLAS_SESSION_TIME_TO_LIVE,
            "atlas-execution-plan", ATLAS_SESSION_TIME_TO_LIVE);

    /**
     * @param distributedDataProvider the configured provider backing all cluster-wide caches
     * @return the cache manager for every cache that has to stay coherent across nodes
     */
    @Bean("distributedCacheManager")
    public CacheManager distributedCacheManager(DistributedDataProvider distributedDataProvider) {
        return new DistributedDataCacheManager(distributedDataProvider, EXPIRING_CACHES);
    }

    /**
     * @param distributedCacheManager serves the caches shared across nodes
     * @param blobCacheManager        serves the per-node blob caches
     * @param titleCacheManager       serves the per-node title caches
     * @return the cache manager Spring resolves {@code @Cacheable} against
     */
    @Bean
    @Primary
    public CacheManager cacheManager(@Qualifier("distributedCacheManager") CacheManager distributedCacheManager, @Qualifier("blobCacheManager") CacheManager blobCacheManager,
            @Qualifier("titleCacheManager") CacheManager titleCacheManager) {
        return new RoutingCacheManager(distributedCacheManager, blobCacheManager, titleCacheManager);
    }

    /**
     * Creates a cache key generator that includes build information in cache keys.
     *
     * <p>
     * <strong>Rationale:</strong> Including git commit hash and build version in cache keys ensures
     * that cache entries from different application versions don't collide. This is important during
     * rolling deployments where nodes running different versions coexist temporarily. Without this,
     * serialization incompatibilities between versions could cause errors.
     *
     * @param gitProperties   git commit information (commit hash, branch)
     * @param buildProperties build metadata (version, timestamp)
     * @return a key generator that prefixes cache keys with version information
     */
    @Bean
    public KeyGenerator keyGenerator(GitProperties gitProperties, BuildProperties buildProperties) {
        return new PrefixedKeyGenerator(gitProperties, buildProperties);
    }
}
