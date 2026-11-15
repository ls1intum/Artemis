package de.tum.cit.aet.artemis.core.service.distributed.api.map;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Native map views and snapshots shared by providers backed by a Java {@link Map}. */
public abstract class AbstractDistributedMap<K, V> implements DistributedMap<K, V> {

    private final Map<K, V> delegate;

    protected AbstractDistributedMap(Map<K, V> delegate) {
        this.delegate = delegate;
    }

    @Override
    public Collection<V> values() {
        return delegate.values();
    }

    @Override
    public Set<K> keySet() {
        return delegate.keySet();
    }

    @Override
    public Set<Map.Entry<K, V>> entrySet() {
        return delegate.entrySet();
    }

    @Override
    public HashMap<K, V> getMapCopy() {
        return new HashMap<>(delegate);
    }

    @Override
    public int size() {
        return delegate.size();
    }

    @Override
    public void clear() {
        delegate.clear();
    }
}
