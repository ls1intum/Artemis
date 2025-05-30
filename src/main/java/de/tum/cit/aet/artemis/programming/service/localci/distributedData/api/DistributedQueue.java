package de.tum.cit.aet.artemis.programming.service.localci.distributedData.api;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import com.hazelcast.collection.ItemListener;

public interface DistributedQueue<T> {

    boolean add(T item);

    boolean offer(T item);

    T poll();

    T peek();

    void clear();

    boolean addAll(Collection<T> items);

    void removeAll(Collection<T> items);

    List<T> getAll();

    boolean isEmpty();

    int size();

    // TODO jfr: If we keep this interface for listeners, the Redisson implementation might need an Adapter.
    UUID addItemListener(ItemListener<T> addedItemListener, boolean includeValue);

    void removeItemListener(UUID registrationId);

    // TODO jfr: Maybe instead define our own listener interface later on.
    // void addItemListener(ItemAddListener<T> addedItemListener);
}
