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
 * failure: a blob cache on the distributed manager just quietly transfers megabytes per read, and a cache that has to
 * be identical on every node quietly goes incoherent on the per-node manager.
 */
class RoutingCacheManagerTest {

    private CacheManager distributedCacheManager;

    private CacheManager blobCacheManager;

    private RoutingCacheManager routingCacheManager;

    @BeforeEach
    void setUp() {
        distributedCacheManager = new ConcurrentMapCacheManager("atlas-session-pending-operations", "atlas-execution-plan");
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
        Cache cache = routingCacheManager.getCache("atlas-session-pending-operations");

        assertThat(cache).isSameAs(distributedCacheManager.getCache("atlas-session-pending-operations"));
    }

    @Test
    void shouldReportCacheNamesOfEveryManagerSorted() {
        List<String> names = List.copyOf(routingCacheManager.getCacheNames());

        assertThat(names).contains("atlas-session-pending-operations", "atlas-execution-plan").containsAll(BlobCacheConfiguration.BLOB_CACHE_NAMES);
        assertThat(names).as("a stable order keeps the admin cache overview from reshuffling").isSorted();
    }
}
