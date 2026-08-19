package de.tum.cit.aet.artemis.core.config.cache;

import java.util.Collection;
import java.util.TreeSet;

import org.jspecify.annotations.Nullable;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

/**
 * Sends each cache to the manager that suits its value shape.
 *
 * <p>
 * Blob caches (see {@link BlobCacheConfiguration#BLOB_CACHE_NAMES}) go to a bounded per-node cache; everything else goes
 * to the distributed cache manager, which is what the small, read-heavy caches such as the title lookups need in order to
 * stay coherent across nodes.
 *
 * <p>
 * Routing happens here rather than by annotating call sites so that the choice is made in one place and cannot drift as
 * caches are added.
 */
public class RoutingCacheManager implements CacheManager {

    private final CacheManager distributedCacheManager;

    private final CacheManager blobCacheManager;

    public RoutingCacheManager(CacheManager distributedCacheManager, CacheManager blobCacheManager) {
        this.distributedCacheManager = distributedCacheManager;
        this.blobCacheManager = blobCacheManager;
    }

    @Override
    @Nullable
    public Cache getCache(String name) {
        return managerFor(name).getCache(name);
    }

    @Override
    public Collection<String> getCacheNames() {
        // Sorted so the admin cache overview has a stable order regardless of which manager reported a name first.
        Collection<String> names = new TreeSet<>(distributedCacheManager.getCacheNames());
        names.addAll(blobCacheManager.getCacheNames());
        return names;
    }

    /**
     * @param name the cache name
     * @return the manager responsible for the given cache
     */
    private CacheManager managerFor(String name) {
        return BlobCacheConfiguration.BLOB_CACHE_NAMES.contains(name) ? blobCacheManager : distributedCacheManager;
    }
}
