package de.tum.cit.aet.artemis.core.config.cache;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;

import org.jspecify.annotations.Nullable;
import org.springframework.cache.support.AbstractValueAdaptingCache;

import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;

/**
 * Spring {@link org.springframework.cache.Cache} backed by a {@link DistributedMap}.
 *
 * <p>
 * Using the distributed data provider rather than a backend-specific cache manager is what lets a core node run on any
 * configured provider. With a Hazelcast-specific manager the {@code @Cacheable} caches would pin every core node to
 * Hazelcast even when Redis is selected, so the two backends could never be swapped.
 *
 * <p>
 * Null values are supported through {@link AbstractValueAdaptingCache}, which stores
 * {@link org.springframework.cache.support.NullValue} in their place. That matters because several {@code @Cacheable}
 * methods have no {@code unless = "#result == null"} guard and rely on a cached null, while the distributed backends
 * reject a null value outright.
 */
public class DistributedDataCache extends AbstractValueAdaptingCache implements KeyEnumerableCache {

    private final String name;

    private final DistributedMap<Object, Object> store;

    /**
     * @param name  the cache name, which is also the name of the backing distributed map
     * @param store the distributed map holding the entries
     */
    public DistributedDataCache(String name, DistributedMap<Object, Object> store) {
        super(true);
        this.name = name;
        this.store = store;
    }

    @Override
    public final String getName() {
        return name;
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * Exposes the {@link DistributedMap} rather than a backend object such as an {@code IMap}, so that callers reaching
     * for the native store still stay on the abstraction.
     */
    @Override
    public final DistributedMap<Object, Object> getNativeCache() {
        return store;
    }

    @Override
    @Nullable
    protected Object lookup(Object key) {
        return store.get(key);
    }

    @Override
    @Nullable
    public <T> T get(Object key, Callable<T> valueLoader) {
        // Serialise the load across the cluster on the key, so a slow loader runs once rather than once per node. This
        // is the contract of @Cacheable(sync = true), which is the only way this overload is reached.
        store.lock(key);
        try {
            Object existing = lookup(key);
            if (existing != null) {
                // noinspection unchecked
                return (T) fromStoreValue(existing);
            }
            T loaded;
            try {
                loaded = valueLoader.call();
            }
            catch (Exception e) {
                throw new ValueRetrievalException(key, valueLoader, e);
            }
            store.put(key, toStoreValue(loaded));
            return loaded;
        }
        finally {
            store.unlock(key);
        }
    }

    @Override
    public void put(Object key, @Nullable Object value) {
        store.put(key, toStoreValue(value));
    }

    @Override
    @Nullable
    public ValueWrapper putIfAbsent(Object key, @Nullable Object value) {
        return toValueWrapper(store.putIfAbsent(key, toStoreValue(value)));
    }

    @Override
    public void evict(Object key) {
        store.remove(key);
    }

    @Override
    public boolean evictIfPresent(Object key) {
        return store.remove(key) != null;
    }

    @Override
    public void clear() {
        store.clear();
    }

    @Override
    public boolean invalidate() {
        boolean hadEntries = !store.isEmpty();
        store.clear();
        return hadEntries;
    }

    @Override
    public Set<Object> cacheKeys() {
        // Copy, because the caller removes entries while iterating and the backing map is modified by other nodes.
        return new HashSet<>(store.keySet());
    }
}
