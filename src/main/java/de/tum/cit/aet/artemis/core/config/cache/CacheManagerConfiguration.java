package de.tum.cit.aet.artemis.core.config.cache;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

/**
 * Exposes the {@link CacheManager} that {@code @Cacheable} resolves against.
 *
 * <p>
 * It routes by cache name so that large blob values stay on the node that produced them while the small, read-heavy
 * caches remain shared across nodes. See {@link BlobCacheConfiguration} for why that split exists.
 */
@Profile(PROFILE_CORE)
@Lazy
@Configuration
public class CacheManagerConfiguration {

    @Bean
    @Primary
    public CacheManager cacheManager(@Qualifier("distributedCacheManager") CacheManager distributedCacheManager, @Qualifier("blobCacheManager") CacheManager blobCacheManager) {
        return new RoutingCacheManager(distributedCacheManager, blobCacheManager);
    }
}
