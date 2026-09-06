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

    private CacheManager titleCacheManager;

    private RoutingCacheManager routingCacheManager;

    @BeforeEach
    void setUp() {
        distributedCacheManager = new ConcurrentMapCacheManager("notificationParameters", "userCourseNotificationSettingPreset");
        blobCacheManager = new ConcurrentMapCacheManager(BlobCacheConfiguration.BLOB_CACHE_NAMES.toArray(String[]::new));
        titleCacheManager = new ConcurrentMapCacheManager(TitleCacheConfiguration.TITLE_CACHE_NAMES.toArray(String[]::new));
        routingCacheManager = new RoutingCacheManager(distributedCacheManager, blobCacheManager, titleCacheManager);
    }

    @Test
    void shouldRouteBlobCachesToThePerNodeManager() {
        for (String blobCacheName : BlobCacheConfiguration.BLOB_CACHE_NAMES) {
            Cache cache = routingCacheManager.getCache(blobCacheName);

            assertThat(cache).as("%s must be served by the per-node manager", blobCacheName).isSameAs(blobCacheManager.getCache(blobCacheName));
        }
    }

    @Test
    void shouldRouteTitleCachesToThePerNodeManager() {
        for (String titleCacheName : TitleCacheConfiguration.TITLE_CACHE_NAMES) {
            Cache cache = routingCacheManager.getCache(titleCacheName);

            assertThat(cache).as("%s must be served by the per-node manager", titleCacheName).isSameAs(titleCacheManager.getCache(titleCacheName));
        }
    }

    @Test
    void shouldRouteEveryOtherCacheToTheDistributedManager() {
        Cache cache = routingCacheManager.getCache("notificationParameters");

        assertThat(cache).isSameAs(distributedCacheManager.getCache("notificationParameters"));
    }

    @Test
    void shouldReportCacheNamesOfEveryManagerSorted() {
        List<String> names = List.copyOf(routingCacheManager.getCacheNames());

        assertThat(names).contains("notificationParameters", "userCourseNotificationSettingPreset").containsAll(BlobCacheConfiguration.BLOB_CACHE_NAMES)
                .containsAll(TitleCacheConfiguration.TITLE_CACHE_NAMES);
        assertThat(names).as("a stable order keeps the admin cache overview from reshuffling").isSorted();
    }
}
