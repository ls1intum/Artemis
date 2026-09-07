package de.tum.cit.aet.artemis.core.service.distributed.local;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import de.tum.cit.aet.artemis.core.service.distributed.api.set.DistributedSet;

public class LocalSet<T> implements DistributedSet<T> {

    private final Set<T> set = ConcurrentHashMap.newKeySet();

    @Override
    public boolean add(T item) {
        return set.add(item);
    }

    @Override
    public boolean remove(T item) {
        return set.remove(item);
    }

    @Override
    public boolean contains(T item) {
        return set.contains(item);
    }

    @Override
    public Set<T> getAll() {
        return new HashSet<>(set);
    }

    @Override
    public int size() {
        return set.size();
    }

    @Override
    public boolean isEmpty() {
        return set.isEmpty();
    }

    @Override
    public void clear() {
        set.clear();
    }
}
