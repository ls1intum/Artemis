package de.tum.cit.aet.artemis.programming.service.localci.distributed.hazelcast;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.hazelcast.collection.ISet;
import com.hazelcast.collection.ItemEvent;
import com.hazelcast.collection.ItemListener;

import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.set.DistributedSet;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.set.listener.SetItemAddedEvent;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.set.listener.SetItemListener;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.set.listener.SetItemRemovedEvent;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.set.listener.SetListener;

/**
 * Hazelcast implementation of the DistributedSet interface.
 *
 * @param <E> the type of elements in this set
 */
public class HazelcastDistributedSet<E> implements DistributedSet<E> {

    private final ISet<E> set;

    private final ConcurrentHashMap<UUID, java.util.UUID> listenerRegistrations = new ConcurrentHashMap<>();

    public HazelcastDistributedSet(ISet<E> set) {
        this.set = set;
    }

    @Override
    public boolean add(E element) {
        return set.add(element);
    }

    @Override
    public boolean addAll(Collection<? extends E> elements) {
        return set.addAll(elements);
    }

    @Override
    public boolean remove(E element) {
        return set.remove(element);
    }

    @Override
    public boolean removeAll(Collection<?> elements) {
        return set.removeAll(elements);
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
        ItemListener<E> hazelcastListener = new ItemListener<>() {

            @Override
            public void itemAdded(ItemEvent<E> item) {
                listener.itemAdded(new SetItemAddedEvent<>(item.getItem()));
            }

            @Override
            public void itemRemoved(ItemEvent<E> item) {
                listener.itemRemoved(new SetItemRemovedEvent<>(item.getItem()));
            }
        };

        java.util.UUID hazelcastId = set.addItemListener(hazelcastListener, true);
        UUID listenerId = UUID.randomUUID();
        listenerRegistrations.put(listenerId, hazelcastId);
        return listenerId;
    }

    @Override
    public UUID addListener(SetListener listener) {
        ItemListener<E> hazelcastListener = new ItemListener<>() {

            @Override
            public void itemAdded(ItemEvent<E> item) {
                listener.onSetChanged();
            }

            @Override
            public void itemRemoved(ItemEvent<E> item) {
                listener.onSetChanged();
            }
        };

        java.util.UUID hazelcastId = set.addItemListener(hazelcastListener, false);
        UUID listenerId = UUID.randomUUID();
        listenerRegistrations.put(listenerId, hazelcastId);
        return listenerId;
    }

    @Override
    public void removeListener(UUID listenerId) {
        java.util.UUID hazelcastId = listenerRegistrations.remove(listenerId);
        if (hazelcastId != null) {
            set.removeItemListener(hazelcastId);
        }
    }
}
