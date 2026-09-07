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
 * Blob caches (see {@link BlobCacheConfiguration#BLOB_CACHE_NAMES}) and title caches (see
 * {@link TitleCacheConfiguration#TITLE_CACHE_NAMES}) go to a bounded per-node cache, because a network round trip buys
 * nothing for a rendered file or for one column found by primary key. Everything else goes to the distributed cache
 * manager, which is what a cache whose entries must be identical on every node needs.
 *
 * <p>
 * Routing happens here rather than by annotating call sites so that the choice is made in one place and cannot drift as
 * caches are added.
 */
public class RoutingCacheManager implements CacheManager {

    private final CacheManager distributedCacheManager;

    private final CacheManager blobCacheManager;

    private final CacheManager titleCacheManager;

    public RoutingCacheManager(CacheManager distributedCacheManager, CacheManager blobCacheManager, CacheManager titleCacheManager) {
        this.distributedCacheManager = distributedCacheManager;
        this.blobCacheManager = blobCacheManager;
        this.titleCacheManager = titleCacheManager;
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
        names.addAll(titleCacheManager.getCacheNames());
        return names;
    }

    /**
     * @param name the cache name
     * @return the manager responsible for the given cache
     */
    private CacheManager managerFor(String name) {
        if (BlobCacheConfiguration.BLOB_CACHE_NAMES.contains(name)) {
            return blobCacheManager;
        }
        if (TitleCacheConfiguration.TITLE_CACHE_NAMES.contains(name)) {
            return titleCacheManager;
        }
        return distributedCacheManager;
    }
}
