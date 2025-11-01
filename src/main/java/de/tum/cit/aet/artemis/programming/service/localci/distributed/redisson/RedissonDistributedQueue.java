package de.tum.cit.aet.artemis.programming.service.localci.distributed.redisson;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.redisson.api.RQueue;
import org.redisson.api.RTopic;
import org.redisson.api.listener.ListAddListener;
import org.redisson.api.listener.ListRemoveListener;
import org.redisson.client.RedisConnectionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.queue.DistributedQueue;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.queue.listener.QueueItemListener;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.queue.listener.QueueListener;

public class RedissonDistributedQueue<T> implements DistributedQueue<T> {

    private static final Logger log = LoggerFactory.getLogger(RedissonDistributedQueue.class);

    private final RQueue<T> queue;

    private final RTopic notificationTopic;

    private final Map<UUID, Integer> topicListenerRegistrations = new ConcurrentHashMap<>();

    private final Map<UUID, Integer> queueListenerRegistrations = new ConcurrentHashMap<>();

    public RedissonDistributedQueue(RQueue<T> queue, RTopic notificationTopic) {
        this.queue = queue;
        this.notificationTopic = notificationTopic;
    }

    private void publishSafely(Object event) {
        try {
            notificationTopic.publish(event);
        }

        catch (Exception ex) {
            log.error("Failed to publish queue notification. Event: {} for Queue {}", event, queue.getName(), ex);
        }
    }

    @Override
    public boolean add(T item) {
        boolean added = queue.add(item);
        if (added) {
            publishSafely(QueueItemEvent.added(item));
        }
        return added;
    }

    @Override
    public T poll() {
        var item = queue.poll();
        if (item != null) {
            publishSafely(QueueItemEvent.removed(item));
        }
        return item;
    }

    @Override
    public T peek() {
        return queue.peek();
    }

    @Override
    public void clear() {
        List<T> queueCopy = new ArrayList<>(queue);
        queue.clear();
        // use the copy instead so we can notify after clearing the queue
        for (T item : queueCopy) {
            publishSafely(QueueItemEvent.removed(item));
        }
    }

    @Override
    public boolean addAll(Collection<T> items) {
        boolean changed = queue.addAll(items);
        if (changed) {
            for (T item : items) {
                publishSafely(QueueItemEvent.added(item));
            }
        }
        return changed;
    }

    @Override
    public void removeAll(Collection<T> items) {
        for (T item : items) {
            while (queue.remove(item)) {
                publishSafely(QueueItemEvent.removed(item));
            }
        }
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
        int registrationId = notificationTopic.addListener(QueueItemEvent.class, (channel, event) -> {
            if (event.getType() == QueueItemEvent.EventType.ADD) {
                listener.itemAdded((T) event.getItem());
            }
            else if (event.getType() == QueueItemEvent.EventType.REMOVE) {
                listener.itemRemoved((T) event.getItem());
            }
        });
        UUID uuid = UUID.randomUUID();
        topicListenerRegistrations.put(uuid, registrationId);
        return uuid;
    }

    /**
     * Adds a listener that will be notified when items are added or removed from the queue.
     *
     * @param listener the listener to add
     * @return a unique identifier for the registration, which can be used to remove the listener later
     */
    @Override
    public UUID addListener(QueueListener listener) {
        class RedissonQueueListener implements ListAddListener, ListRemoveListener {

            @Override
            public void onListAdd(String name) {
                listener.itemAdded();
            }

            @Override
            public void onListRemove(String name) {
                listener.itemRemoved();
            }
        }
        int registrationId = queue.addListener(new RedissonQueueListener());
        UUID uuid = UUID.randomUUID();
        queueListenerRegistrations.put(uuid, registrationId);
        return uuid;
    }

    @Override
    public void removeListener(UUID uuid) {
        try {
            Integer listenerId = topicListenerRegistrations.get(uuid);
            if (listenerId != null) {
                notificationTopic.removeListener(listenerId);
                topicListenerRegistrations.remove(uuid);
                log.debug("Removed topic listener for UUID: {}", uuid);
                return;
            }
            listenerId = queueListenerRegistrations.get(uuid);

            if (listenerId == null) {
                log.warn("No listener found for UUID: {}", uuid);
                return;
            }
            queue.removeListener(listenerId);
            queueListenerRegistrations.remove(uuid);
            log.debug("Removed queue listener for UUID: {}", uuid);
        }
        catch (RedisConnectionException e) {
            log.error("Could not remove listener due to Redis connection exception.", e);
        }
    }

    @Override
    public String getName() {
        return queue.getName();
    }
}
