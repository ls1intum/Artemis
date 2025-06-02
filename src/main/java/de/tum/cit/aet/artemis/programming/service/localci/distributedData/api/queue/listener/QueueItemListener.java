package de.tum.cit.aet.artemis.programming.service.localci.distributedData.api.queue.listener;

public interface QueueItemListener<T> {

    void itemAdded(T item);

    void itemRemoved(T item);
}
