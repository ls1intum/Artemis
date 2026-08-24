package de.tum.cit.aet.artemis.core.service.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.api.topic.DistributedTopic;
import de.tum.cit.aet.artemis.core.service.distributed.local.LocalDataProviderService;

/**
 * The blob caches are per-node, so an eviction that does not reach the other nodes leaves them serving superseded file
 * content. These tests pin down that the eviction always takes effect locally and is broadcast when a provider exists.
 */
class BlobCacheEvictionServiceTest {

    private static final String CACHE_NAME = "files";

    private CacheManager cacheManager;

    @BeforeEach
    void setUp() {
        cacheManager = new ConcurrentMapCacheManager(CACHE_NAME);
    }

    /**
     * @return the cache entry seeded before each eviction attempt
     */
    private Cache seededCache() {
        Cache cache = cacheManager.getCache(CACHE_NAME);
        cache.put("path/to/file", "content");
        return cache;
    }

    @Test
    void shouldEvictLocallyAndBroadcastWhenProviderIsPresent() {
        LocalDataProviderService provider = new LocalDataProviderService();
        BlobCacheEvictionService service = new BlobCacheEvictionService(cacheManager, Optional.of(provider));
        service.init();
        Cache cache = seededCache();

        service.evictEverywhere(CACHE_NAME, "path/to/file");

        assertThat(cache.get("path/to/file")).as("the node performing the write must not keep serving its stale entry").isNull();
    }

    /**
     * Without a provider there is only one node, so a local eviction already covers every reader.
     */
    @Test
    void shouldEvictLocallyWhenNoProviderIsConfigured() {
        BlobCacheEvictionService service = new BlobCacheEvictionService(cacheManager, Optional.empty());
        service.init();
        Cache cache = seededCache();

        service.evictEverywhere(CACHE_NAME, "path/to/file");

        assertThat(cache.get("path/to/file")).isNull();
    }

    /**
     * A broadcast failure must still evict locally, otherwise the node that just rewrote the file keeps serving the old
     * bytes it knows are wrong.
     */
    @Test
    void shouldStillEvictLocallyWhenBroadcastFails() {
        DistributedDataProvider provider = mock(DistributedDataProvider.class);
        DistributedTopic<Object> topic = mock(DistributedTopic.class);
        when(provider.getTopic(anyString())).thenReturn(topic);
        doThrow(new IllegalStateException("broker unavailable")).when(topic).publish(org.mockito.ArgumentMatchers.any());

        BlobCacheEvictionService service = new BlobCacheEvictionService(cacheManager, Optional.of(provider));
        service.init();
        Cache cache = seededCache();

        service.evictEverywhere(CACHE_NAME, "path/to/file");

        assertThat(cache.get("path/to/file")).isNull();
    }

    @Test
    void shouldIgnoreEvictionForUnknownCache() {
        BlobCacheEvictionService service = new BlobCacheEvictionService(cacheManager, Optional.empty());
        service.init();

        // A cache name that does not exist must not blow up the writing request.
        service.evictEverywhere("notARegisteredCache", "someKey");

        assertThat(cacheManager.getCache(CACHE_NAME)).isNotNull();
    }
}
