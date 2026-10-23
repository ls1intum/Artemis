package de.tum.cit.aet.artemis.core.service.distributed.api.set;

import java.util.HashSet;
import java.util.Set;

/** Common delegation for providers whose native set implements {@link Set}. */
public abstract class AbstractDistributedSet<T> implements DistributedSet<T> {

    protected final Set<T> set;

    protected AbstractDistributedSet(Set<T> set) {
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
        // A detached snapshot also avoids exposing the native set to callers.
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
