package de.tum.cit.aet.artemis.programming.service.localci.distributedData.hazelcast;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import com.hazelcast.collection.IQueue;
import com.hazelcast.collection.ItemListener;

import de.tum.cit.aet.artemis.programming.service.localci.distributedData.api.DistributedQueue;

public class HazelcastDistributedQueue<T> implements DistributedQueue<T> {

    private final IQueue<T> queue;

    public HazelcastDistributedQueue(IQueue<T> queue) {
        this.queue = queue;
    }

    @Override
    public boolean add(T item) {
        return queue.add(item);
    }

    @Override
    public boolean offer(T item) {
        return queue.offer(item);
    }

    @Override
    public T poll() {
        return queue.poll();
    }

    @Override
    public T peek() {
        return queue.peek();
    }

    @Override
    public void clear() {
        queue.clear();
    }

    @Override
    public boolean addAll(Collection<T> items) {
        return queue.addAll(items);
    }

    @Override
    public void removeAll(Collection<T> items) {
        queue.removeAll(items);
    }

    @Override
    public List<T> getAll() {
        return new ArrayList<>(queue);
    }

    @Override
    public boolean isEmpty() {
        return queue.isEmpty();
    }

    @Override
    public int size() {
        return queue.size();
    }

    @Override
    public UUID addItemListener(ItemListener<T> addedItemListener, boolean includeValue) {
        return queue.addItemListener(addedItemListener, includeValue);
    }

    @Override
    public void removeItemListener(UUID registrationId) {
        queue.removeItemListener(registrationId);
    }
}
