package de.tum.cit.aet.artemis.programming.service.localci.distributed.hazelcast;

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

import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.queue.DistributedQueue;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.queue.listener.QueueItemListener;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.queue.listener.QueueListener;

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
    public String getName() {
        return queue.getName();
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
        return queue.addItemListener(new ItemListener<T>() {

            @Override
            public void itemAdded(ItemEvent<T> event) {
                listener.itemAdded(event.getItem());
            }

            @Override
            public void itemRemoved(ItemEvent<T> event) {
                listener.itemRemoved(event.getItem());
            }
        }, true);
    }

    @Override
    public UUID addListener(QueueListener listener) {
        return queue.addItemListener(new ItemListener<>() {

            @Override
            public void itemAdded(ItemEvent<T> item) {
                listener.itemAdded();
            }

            @Override
            public void itemRemoved(ItemEvent<T> item) {
                listener.itemRemoved();
            }
        }, false);
    }

    @Override
    public void removeListener(UUID registrationId) {
        try {
            queue.removeItemListener(registrationId);
        }
        catch (HazelcastInstanceNotActiveException e) {
            log.error("Could not remove listener from queue '{}' as hazelcast instance is not active.", queue.getName(), e);
        }
    }
}
