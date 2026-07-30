package de.tum.cit.aet.artemis.core.config.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

/**
 * Verifies that caches reach the manager appropriate to their value shape. Routing the wrong way is not a visible
 * failure: a blob cache on the distributed manager just quietly transfers megabytes per read, and a small cache on the
 * per-node manager quietly goes incoherent across nodes.
 */
class RoutingCacheManagerTest {

    private CacheManager distributedCacheManager;

    private CacheManager blobCacheManager;

    private RoutingCacheManager routingCacheManager;

    @BeforeEach
    void setUp() {
        distributedCacheManager = new ConcurrentMapCacheManager("courseTitle", "exerciseTitle");
        blobCacheManager = new ConcurrentMapCacheManager(BlobCacheConfiguration.BLOB_CACHE_NAMES.toArray(String[]::new));
        routingCacheManager = new RoutingCacheManager(distributedCacheManager, blobCacheManager);
    }

    @Test
    void shouldRouteBlobCachesToThePerNodeManager() {
        for (String blobCacheName : BlobCacheConfiguration.BLOB_CACHE_NAMES) {
            Cache cache = routingCacheManager.getCache(blobCacheName);

            assertThat(cache).as("%s must be served by the per-node manager", blobCacheName).isSameAs(blobCacheManager.getCache(blobCacheName));
        }
    }

    @Test
    void shouldRouteEveryOtherCacheToTheDistributedManager() {
        Cache cache = routingCacheManager.getCache("courseTitle");

        assertThat(cache).isSameAs(distributedCacheManager.getCache("courseTitle"));
    }

    @Test
    void shouldReportCacheNamesOfBothManagersSorted() {
        List<String> names = List.copyOf(routingCacheManager.getCacheNames());

        assertThat(names).contains("courseTitle", "exerciseTitle").containsAll(BlobCacheConfiguration.BLOB_CACHE_NAMES);
        assertThat(names).as("a stable order keeps the admin cache overview from reshuffling").isSorted();
    }

    @Test
    void shouldExposeTheDistributedManagerForKeyLevelAccess() {
        assertThat(routingCacheManager.getDistributedCacheManager()).isSameAs(distributedCacheManager);
    }
}
