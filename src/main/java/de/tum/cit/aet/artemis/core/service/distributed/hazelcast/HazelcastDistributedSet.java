package de.tum.cit.aet.artemis.core.service.distributed.hazelcast;

import java.util.HashSet;
import java.util.Set;

import com.hazelcast.collection.ISet;

import de.tum.cit.aet.artemis.core.service.distributed.api.set.DistributedSet;

public class HazelcastDistributedSet<T> implements DistributedSet<T> {

    private final ISet<T> set;

    public HazelcastDistributedSet(ISet<T> set) {
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
        // Copy, because ISet iteration is not safe while the set is mutated concurrently.
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
