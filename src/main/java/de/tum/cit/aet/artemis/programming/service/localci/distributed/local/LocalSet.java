package de.tum.cit.aet.artemis.programming.service.localci.distributed.local;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.set.DistributedSet;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.set.listener.SetItemAddedEvent;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.set.listener.SetItemListener;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.set.listener.SetItemRemovedEvent;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.set.listener.SetListener;

/**
 * Local (single-node) implementation of the DistributedSet interface.
 *
 * @param <E> the type of elements in this set
 */
public class LocalSet<E> implements DistributedSet<E> {

    private static final Logger log = LoggerFactory.getLogger(LocalSet.class);

    private final Set<E> set = ConcurrentHashMap.newKeySet();

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    private final ConcurrentHashMap<UUID, SetItemListener<E>> itemListeners = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<UUID, SetListener> setListeners = new ConcurrentHashMap<>();

    private final ExecutorService notificationExecutor;

    public LocalSet() {
        this(Executors.newCachedThreadPool(BasicThreadFactory.builder().namingPattern("local-set-listener-%d").daemon().build()));
    }

    public LocalSet(ExecutorService notificationExecutor) {
        this.notificationExecutor = notificationExecutor;
    }

    @Override
    public boolean add(E element) {
        lock.writeLock().lock();
        try {
            boolean added = set.add(element);
            if (added) {
                notifyItemAdded(element);
            }
            return added;
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public boolean addAll(Collection<? extends E> elements) {
        lock.writeLock().lock();
        try {
            boolean changed = false;
            for (E element : elements) {
                if (set.add(element)) {
                    changed = true;
                    notifyItemAdded(element);
                }
            }
            return changed;
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public boolean remove(E element) {
        lock.writeLock().lock();
        try {
            boolean removed = set.remove(element);
            if (removed) {
                notifyItemRemoved(element);
            }
            return removed;
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public boolean removeAll(Collection<?> elements) {
        lock.writeLock().lock();
        try {
            boolean changed = false;
            for (Object element : elements) {
                if (set.remove(element)) {
                    changed = true;
                    @SuppressWarnings("unchecked")
                    E typedElement = (E) element;
                    notifyItemRemoved(typedElement);
                }
            }
            return changed;
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public boolean contains(E element) {
        lock.readLock().lock();
        try {
            return set.contains(element);
        }
        finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public boolean containsAll(Collection<?> elements) {
        lock.readLock().lock();
        try {
            return set.containsAll(elements);
        }
        finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public int size() {
        lock.readLock().lock();
        try {
            return set.size();
        }
        finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public boolean isEmpty() {
        lock.readLock().lock();
        try {
            return set.isEmpty();
        }
        finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void clear() {
        lock.writeLock().lock();
        try {
            for (E element : new HashSet<>(set)) {
                set.remove(element);
                notifyItemRemoved(element);
            }
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public Set<E> getSetCopy() {
        lock.readLock().lock();
        try {
            return new HashSet<>(set);
        }
        finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public UUID addItemListener(SetItemListener<E> listener) {
        UUID listenerId = UUID.randomUUID();
        itemListeners.put(listenerId, listener);
        return listenerId;
    }

    @Override
    public UUID addListener(SetListener listener) {
        UUID listenerId = UUID.randomUUID();
        setListeners.put(listenerId, listener);
        return listenerId;
    }

    @Override
    public void removeListener(UUID listenerId) {
        itemListeners.remove(listenerId);
        setListeners.remove(listenerId);
    }

    private void notifyItemAdded(E element) {
        SetItemAddedEvent<E> event = new SetItemAddedEvent<>(element);
        for (SetItemListener<E> listener : itemListeners.values()) {
            notificationExecutor.execute(() -> {
                try {
                    listener.itemAdded(event);
                }
                catch (Exception e) {
                    log.warn("Error notifying item listener about added item", e);
                }
            });
        }
        for (SetListener listener : setListeners.values()) {
            notificationExecutor.execute(() -> {
                try {
                    listener.onSetChanged();
                }
                catch (Exception e) {
                    log.warn("Error notifying set listener about change", e);
                }
            });
        }
    }

    private void notifyItemRemoved(E element) {
        SetItemRemovedEvent<E> event = new SetItemRemovedEvent<>(element);
        for (SetItemListener<E> listener : itemListeners.values()) {
            notificationExecutor.execute(() -> {
                try {
                    listener.itemRemoved(event);
                }
                catch (Exception e) {
                    log.warn("Error notifying item listener about removed item", e);
                }
            });
        }
        for (SetListener listener : setListeners.values()) {
            notificationExecutor.execute(() -> {
                try {
                    listener.onSetChanged();
                }
                catch (Exception e) {
                    log.warn("Error notifying set listener about change", e);
                }
            });
        }
    }
}
