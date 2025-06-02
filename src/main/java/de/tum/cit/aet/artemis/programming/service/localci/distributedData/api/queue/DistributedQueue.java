package de.tum.cit.aet.artemis.programming.service.localci.distributedData.api.queue;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import de.tum.cit.aet.artemis.programming.service.localci.distributedData.api.queue.listener.QueueItemListener;

public interface DistributedQueue<T> {

    boolean add(T item);

    // boolean offer(T item);

    T poll();

    T peek();

    void clear();

    boolean addAll(Collection<T> items);

    void removeAll(Collection<T> items);

    List<T> getAll();

    boolean isEmpty();

    int size();

    UUID addItemListener(QueueItemListener<T> listener);

    void removeItemListener(UUID registrationId);
}
