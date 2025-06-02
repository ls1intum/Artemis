package de.tum.cit.aet.artemis.programming.service.localci.distributedData.hazelcast;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hazelcast.collection.IQueue;
import com.hazelcast.collection.ItemEvent;
import com.hazelcast.collection.ItemListener;
import com.hazelcast.core.HazelcastInstanceNotActiveException;

import de.tum.cit.aet.artemis.programming.service.localci.distributedData.api.queue.DistributedQueue;
import de.tum.cit.aet.artemis.programming.service.localci.distributedData.api.queue.listener.QueueItemListener;

public class HazelcastDistributedQueue<T> implements DistributedQueue<T> {

    private static final Logger log = LoggerFactory.getLogger(HazelcastDistributedQueue.class);

    private final IQueue<T> queue;

    public HazelcastDistributedQueue(IQueue<T> queue) {
        this.queue = queue;
    }

    @Override
    public boolean add(T item) {
        return queue.add(item);
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
    public UUID addItemListener(QueueItemListener<T> listener) {
        ItemListener<T> hazelcastListener = new ItemListener<>() {

            @Override
            public void itemAdded(ItemEvent<T> event) {
                listener.itemAdded(event.getItem());
            }

            @Override
            public void itemRemoved(ItemEvent<T> event) {
                listener.itemRemoved(event.getItem());
            }
        };
        return queue.addItemListener(hazelcastListener, true);
    }

    @Override
    public void removeItemListener(UUID registrationId) {
        try {
            queue.removeItemListener(registrationId);
        }
        catch (HazelcastInstanceNotActiveException e) {
            log.error("Could not remove listener from queue '{}' as hazelcast instance is not active.", queue.getName(), e);
        }
    }
}
