package de.tum.cit.aet.artemis.core.config.cache;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;

/**
 * Serves every cluster-wide {@code @Cacheable} cache from the configured distributed data provider.
 *
 * <p>
 * <strong>Entry lifetimes.</strong> Most of these caches are invalidated explicitly by their writers and are therefore
 * requested without expiry. The other caches declare their lifetimes in {@code timeToLivePerCache}.
 */
public class DistributedDataCacheManager implements CacheManager {

    private final DistributedDataProvider distributedDataProvider;

    private final Map<String, Duration> timeToLivePerCache;

    private final ConcurrentHashMap<String, Cache> caches = new ConcurrentHashMap<>();

    /**
     * @param distributedDataProvider the provider backing every cache
     * @param timeToLivePerCache      cache names that expire, mapped to how long their entries live; every other cache
     *                                    is created without expiry
     */
    public DistributedDataCacheManager(DistributedDataProvider distributedDataProvider, Map<String, Duration> timeToLivePerCache) {
        this.distributedDataProvider = distributedDataProvider;
        this.timeToLivePerCache = Map.copyOf(timeToLivePerCache);
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * Caches are created on demand because the set of cache names is spread over
     * the {@code @Cacheable} annotations of every module and is not known up front.
     */
    @Override
    @Nullable
    public Cache getCache(String name) {
        return caches.computeIfAbsent(name, this::createCache);
    }

    @Override
    public Collection<String> getCacheNames() {
        return new TreeSet<>(Set.copyOf(caches.keySet()));
    }

    private Cache createCache(String name) {
        Duration timeToLive = timeToLivePerCache.get(name);
        var store = timeToLive == null ? distributedDataProvider.<Object, Object>getMap(name) : distributedDataProvider.<Object, Object>getExpiringMap(name, timeToLive);
        return new DistributedDataCache(name, store);
    }
}
