package de.tum.cit.aet.artemis.core.service.distributed.redisson;

import java.util.HashSet;
import java.util.Set;

import org.redisson.api.RSet;

import de.tum.cit.aet.artemis.core.service.distributed.api.set.DistributedSet;

public class RedissonDistributedSet<T> implements DistributedSet<T> {

    private final RSet<T> set;

    public RedissonDistributedSet(RSet<T> set) {
        this.set = set;
    }

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
        return new HashSet<>(set.readAll());
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
        set.delete();
    }
}
