package de.tum.cit.aet.artemis.programming.service.localci.distributed.local;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.queue.DistributedQueue;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.queue.listener.QueueItemListener;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.queue.listener.QueueListener;

public class LocalQueue<T> implements DistributedQueue<T> {

    private static final Logger log = LoggerFactory.getLogger(LocalQueue.class);

    private final Queue<T> queue;

    private final String name;

    private final ReentrantLock lock = new ReentrantLock();

    // Listeners that require the specific changed items on add/update/remove
    private final ConcurrentHashMap<UUID, QueueItemListener<T>> queueItemListeners = new ConcurrentHashMap<>();

    // Simplified listeners that do not require changed entry but just notification
    private final ConcurrentHashMap<UUID, QueueListener> queueListeners = new ConcurrentHashMap<>();

    private static final ExecutorService notificationExecutor = Executors
            .newCachedThreadPool(new ThreadFactoryBuilder().setNameFormat("local-queue-listener-%d").setDaemon(true).build());

    public LocalQueue(Queue<T> queue, String name) {
        this.queue = queue;
        this.name = name;
    }

    @Override
    public boolean add(T item) {
        lock.lock();
        boolean success = false;
        try {
            success = queue.offer(item);
            return success;
        }
        finally {
            lock.unlock();
            if (success) {
                notifyAdded(item);
            }
        }
    }

    @Override
    public T poll() {
        lock.lock();
        T item;
        try {
            item = queue.poll();
        }
        finally {
            lock.unlock();
        }
        if (item != null) {
            notifyRemoved(item);
        }
        return item;
    }

    @Override
    public T peek() {
        lock.lock();
        try {
            return queue.peek();
        }
        finally {
            lock.unlock();
        }
    }

    @Override
    public void clear() {
        lock.lock();
        List<T> items;
        try {
            items = queue.stream().toList();
            queue.clear();
        }
        finally {
            lock.unlock();
        }
        for (T item : items) {
            notifyRemoved(item);
        }
    }

    @Override
    public boolean addAll(Collection<T> items) {
        lock.lock();
        ArrayList<T> addedItems = new ArrayList<>();
        try {
            for (T item : items) {
                if (queue.offer(item)) {
                    addedItems.add(item);
                }
            }
            return !addedItems.isEmpty();
        }
        finally {
            lock.unlock();
            for (T item : addedItems) {
                notifyAdded(item);
            }
        }
    }

    @Override
    public void removeAll(Collection<T> items) {
        List<T> removedItems = new ArrayList<>();
        lock.lock();
        try {
            for (T item : items) {
                if (queue.remove(item)) {
                    removedItems.add(item);
                }
            }
        }
        finally {
            lock.unlock();
        }
        for (T item : removedItems) {
            notifyRemoved(item);
        }
    }

    @Override
    public List<T> getAll() {
        lock.lock();
        try {
            return queue.stream().toList();
        }
        finally {
            lock.unlock();
        }
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean isEmpty() {
        lock.lock();
        try {
            return queue.isEmpty();
        }
        finally {
            lock.unlock();
        }
    }

    @Override
    public int size() {
        lock.lock();
        try {
            return queue.size();
        }
        finally {
            lock.unlock();
        }
    }

    @Override
    public UUID addItemListener(QueueItemListener<T> listener) {
        UUID listenerId = UUID.randomUUID();
        queueItemListeners.put(listenerId, listener);
        return listenerId;
    }

    @Override
    public UUID addListener(QueueListener listener) {
        UUID listenerId = UUID.randomUUID();
        queueListeners.put(listenerId, listener);
        return listenerId;
    }

    @Override
    public void removeListener(UUID registrationId) {
        queueItemListeners.remove(registrationId);
        queueListeners.remove(registrationId);
    }

    private void notifyAdded(T item) {
        notificationExecutor.execute(() -> {
            for (QueueItemListener<T> listener : queueItemListeners.values()) {
                try {
                    listener.itemAdded(item);
                }
                catch (Exception e) {
                    log.error("Error in queue item listener (itemAdded)", e);
                }
            }
            for (QueueListener listener : queueListeners.values()) {
                try {
                    listener.itemAdded();
                }
                catch (Exception e) {
                    log.error("Error in queue listener (itemAdded)", e);
                }
            }
        });
    }

    private void notifyRemoved(T item) {
        notificationExecutor.execute(() -> {
            for (QueueItemListener<T> listener : queueItemListeners.values()) {
                try {
                    listener.itemRemoved(item);
                }
                catch (Exception e) {
                    log.error("Error in queue item listener (itemRemoved)", e);
                }
            }
            for (QueueListener listener : queueListeners.values()) {
                try {
                    listener.itemRemoved();
                }
                catch (Exception e) {
                    log.error("Error in queue listener (itemRemoved)", e);
                }
            }
        });
    }
}
