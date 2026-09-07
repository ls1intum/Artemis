package de.tum.cit.aet.artemis.core.service.distributed.redisson;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.redisson.api.RQueue;
import org.redisson.api.RTopic;
import org.redisson.client.RedisConnectionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tum.cit.aet.artemis.core.service.distributed.api.queue.DistributedQueue;
import de.tum.cit.aet.artemis.core.service.distributed.api.queue.listener.QueueItemListener;
import de.tum.cit.aet.artemis.core.service.distributed.api.queue.listener.QueueListener;

public class RedissonDistributedQueue<T> implements DistributedQueue<T> {

    private static final Logger log = LoggerFactory.getLogger(RedissonDistributedQueue.class);

    private final RQueue<T> queue;

    private final RTopic notificationTopic;

    private final Map<UUID, Integer> topicListenerRegistrations = new ConcurrentHashMap<>();

    /**
     * The name callers know this queue by, without the schema-version namespace the Redis key carries.
     */
    private final String logicalName;

    public RedissonDistributedQueue(RQueue<T> queue, RTopic notificationTopic, String logicalName) {
        this.queue = queue;
        this.notificationTopic = notificationTopic;
        this.logicalName = logicalName;
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
        int registrationId = notificationTopic.addListener(QueueItemEvent.class, (_, event) -> {
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
     * Adds a listener that will be notified when items are added to or removed from the queue.
     *
     * <p>
     * This subscribes to the same notification topic as {@link #addItemListener(QueueItemListener)} rather than to
     * Redisson's {@code ListAddListener}/{@code ListRemoveListener}. Those are driven by Redis keyspace notifications,
     * which would make the listener silently depend on a {@code notify-keyspace-events} server setting containing
     * {@code El}, and they only fire for {@code rpush}. A priority queue inserts in the middle via {@code LINSERT},
     * so keyspace-based listeners would miss exactly the items whose ordering matters.
     *
     * @param listener the listener to add
     * @return a unique identifier for the registration, which can be used to remove the listener later
     */
    @Override
    public UUID addListener(QueueListener listener) {
        int registrationId = notificationTopic.addListener(QueueItemEvent.class, (_, event) -> {
            if (event.getType() == QueueItemEvent.EventType.ADD) {
                listener.itemAdded();
            }
            else if (event.getType() == QueueItemEvent.EventType.REMOVE) {
                listener.itemRemoved();
            }
        });
        UUID uuid = UUID.randomUUID();
        topicListenerRegistrations.put(uuid, registrationId);
        return uuid;
    }

    @Override
    public void removeListener(UUID uuid) {
        try {
            Integer listenerId = topicListenerRegistrations.get(uuid);
            if (listenerId == null) {
                log.warn("No listener found for UUID: {}", uuid);
                return;
            }
            notificationTopic.removeListener(listenerId);
            topicListenerRegistrations.remove(uuid);
            log.debug("Removed topic listener for UUID: {}", uuid);
        }
        catch (RedisConnectionException e) {
            log.error("Could not remove listener due to Redis connection exception.", e);
        }
    }

    @Override
    public String getName() {
        // The logical name rather than the Redis key: the namespace is an implementation detail of this backend, and
        // Hazelcast answers with the plain name, so leaking the prefix here would make the two disagree.
        return logicalName;
    }
}
