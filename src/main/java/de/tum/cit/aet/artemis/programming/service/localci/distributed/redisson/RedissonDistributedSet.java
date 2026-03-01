package de.tum.cit.aet.artemis.programming.service.localci.distributed.redisson;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.redisson.api.RSet;
import org.redisson.api.RTopic;
import org.redisson.client.RedisConnectionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.set.DistributedSet;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.set.listener.SetItemAddedEvent;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.set.listener.SetItemListener;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.set.listener.SetItemRemovedEvent;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.set.listener.SetListener;

/**
 * Redisson implementation of the DistributedSet interface.
 *
 * @param <E> the type of elements in this set
 */
public class RedissonDistributedSet<E> implements DistributedSet<E> {

    private static final Logger log = LoggerFactory.getLogger(RedissonDistributedSet.class);

    private final RSet<E> set;

    private final RTopic notificationTopic;

    private final Map<UUID, Integer> listenerRegistrations = new ConcurrentHashMap<>();

    public RedissonDistributedSet(RSet<E> set, RTopic notificationTopic) {
        this.set = set;
        this.notificationTopic = notificationTopic;
    }

    private void publishSafely(Object event) {
        try {
            notificationTopic.publish(event);
        }
        catch (Exception ex) {
            log.warn("Failed to publish set notification. Event: {} for set {}", event, set.getName(), ex);
        }
    }

    @Override
    public boolean add(E element) {
        boolean added = set.add(element);
        if (added) {
            publishSafely(SetItemEvent.added(element));
        }
        return added;
    }

    @Override
    public boolean addAll(Collection<? extends E> elements) {
        boolean changed = false;
        for (E element : elements) {
            if (set.add(element)) {
                changed = true;
                publishSafely(SetItemEvent.added(element));
            }
        }
        return changed;
    }

    @Override
    public boolean remove(E element) {
        boolean removed = set.remove(element);
        if (removed) {
            publishSafely(SetItemEvent.removed(element));
        }
        return removed;
    }

    @Override
    public boolean removeAll(Collection<?> elements) {
        boolean changed = false;
        for (Object element : elements) {
            if (set.remove(element)) {
                changed = true;
                @SuppressWarnings("unchecked")
                E typedElement = (E) element;
                publishSafely(SetItemEvent.removed(typedElement));
            }
        }
        return changed;
    }

    @Override
    public boolean contains(E element) {
        return set.contains(element);
    }

    @Override
    public boolean containsAll(Collection<?> elements) {
        return set.containsAll(elements);
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

    @Override
    public Set<E> getSetCopy() {
        return new HashSet<>(set);
    }

    @Override
    public UUID addItemListener(SetItemListener<E> listener) {
        int registrationId = notificationTopic.addListener(SetItemEvent.class, (channel, event) -> {
            @SuppressWarnings("unchecked")
            SetItemEvent<E> setItemEvent = (SetItemEvent<E>) event;
            if (event.getType() == SetItemEvent.EventType.ADD) {
                listener.itemAdded(new SetItemAddedEvent<>(setItemEvent.getItem()));
            }
            else if (event.getType() == SetItemEvent.EventType.REMOVE) {
                listener.itemRemoved(new SetItemRemovedEvent<>(setItemEvent.getItem()));
            }
        });
        UUID uuid = UUID.randomUUID();
        listenerRegistrations.put(uuid, registrationId);
        return uuid;
    }

    @Override
    public UUID addListener(SetListener listener) {
        int registrationId = notificationTopic.addListener(SetItemEvent.class, (channel, event) -> {
            listener.onSetChanged();
        });
        UUID uuid = UUID.randomUUID();
        listenerRegistrations.put(uuid, registrationId);
        return uuid;
    }

    @Override
    public void removeListener(UUID uuid) {
        try {
            Integer listenerId = listenerRegistrations.get(uuid);
            if (listenerId == null) {
                log.warn("No listener found for UUID: {}", uuid);
                return;
            }
            notificationTopic.removeListener(listenerId);
            listenerRegistrations.remove(uuid);
        }
        catch (RedisConnectionException e) {
            log.error("Could not remove listener due to Redis connection exception.", e);
        }
    }
}
