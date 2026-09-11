package de.tum.cit.aet.artemis.core.service.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
class PerNodeCacheEvictionServiceTest {

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
        PerNodeCacheEvictionService service = new PerNodeCacheEvictionService(cacheManager, Optional.of(provider));
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
        PerNodeCacheEvictionService service = new PerNodeCacheEvictionService(cacheManager, Optional.empty());
        service.init();
        Cache cache = seededCache();

        service.evictEverywhere(CACHE_NAME, "path/to/file");

        assertThat(cache.get("path/to/file")).isNull();
    }

    /**
     * The point of the broadcast: a write on one node has to drop the entry on the others, which keep their own copy.
     */
    @Test
    void shouldApplyAnEvictionAnotherNodePublished() {
        LocalDataProviderService provider = new LocalDataProviderService();
        PerNodeCacheEvictionService writingNode = new PerNodeCacheEvictionService(new ConcurrentMapCacheManager(CACHE_NAME), Optional.of(provider));
        PerNodeCacheEvictionService readingNode = new PerNodeCacheEvictionService(cacheManager, Optional.of(provider));
        writingNode.init();
        readingNode.init();
        Cache readingNodeCache = seededCache();

        writingNode.evictEverywhere(CACHE_NAME, "path/to/file");

        assertThat(readingNodeCache.get("path/to/file")).as("a node that did not perform the write must still drop its copy").isNull();
    }

    /**
     * A node must not apply its own broadcast. It already evicted synchronously, so the late callback can only drop an
     * entry that a request has read back in since, which is how a renamed title briefly disappeared again.
     * <p>
     * Counted rather than observed on the cache: the local provider delivers a published message within
     * {@code publish}, so a second eviction would land before a test could read anything back in, and the entry would
     * look correct while the node had in fact evicted twice.
     */
    @Test
    void shouldNotApplyItsOwnBroadcastASecondTime() {
        Cache cache = mock(Cache.class);
        CacheManager mockedCacheManager = mock(CacheManager.class);
        when(mockedCacheManager.getCache(CACHE_NAME)).thenReturn(cache);
        PerNodeCacheEvictionService service = new PerNodeCacheEvictionService(mockedCacheManager, Optional.of(new LocalDataProviderService()));
        service.init();

        service.evictEverywhere(CACHE_NAME, "path/to/file");

        verify(cache, times(1)).evictIfPresent("path/to/file");
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

        PerNodeCacheEvictionService service = new PerNodeCacheEvictionService(cacheManager, Optional.of(provider));
        service.init();
        Cache cache = seededCache();

        service.evictEverywhere(CACHE_NAME, "path/to/file");

        assertThat(cache.get("path/to/file")).isNull();
    }

    @Test
    void shouldIgnoreEvictionForUnknownCache() {
        PerNodeCacheEvictionService service = new PerNodeCacheEvictionService(cacheManager, Optional.empty());
        service.init();

        // A cache name that does not exist must not blow up the writing request.
        service.evictEverywhere("notARegisteredCache", "someKey");

        assertThat(cacheManager.getCache(CACHE_NAME)).isNotNull();
    }
}
