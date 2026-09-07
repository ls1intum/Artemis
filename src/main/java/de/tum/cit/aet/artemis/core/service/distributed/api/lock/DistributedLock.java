package de.tum.cit.aet.artemis.core.service.distributed.api.lock;

import java.time.Duration;

/**
 * A mutex shared across all nodes of the cluster, identified by name.
 *
 * <p>
 * Exists so that callers needing a plain cluster-wide mutex stop borrowing {@code DistributedMap.lock(key)} on an
 * unrelated map, which obscures the intent and ties the lock's lifetime to that map.
 *
 * <p>
 * <strong>Guarantees.</strong> None of the backends provide a consensus-backed (CP) lock: Hazelcast's CP subsystem is
 * not enabled in Artemis and needs at least three members, and Redisson's {@code RLock} on a single Redis is likewise
 * AP. A network partition or a node dying at the wrong moment can therefore let two holders believe they own the lock.
 * Use this to de-duplicate work, not to protect an invariant whose violation cannot be tolerated. Locks are reentrant
 * for the owning thread and must be released in a {@code finally} block.
 */
public interface DistributedLock {

    /**
     * Acquires the lock, blocking until it is available.
     */
    void lock();

    /**
     * Attempts to acquire the lock within the given timeout.
     *
     * @param timeout how long to wait
     * @return true if the lock was acquired
     */
    boolean tryLock(Duration timeout);

    /**
     * Releases the lock. Must only be called by the thread holding it.
     */
    void unlock();
}
