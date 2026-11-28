package de.tum.cit.aet.artemis.core.service.distributed.local;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import de.tum.cit.aet.artemis.core.service.distributed.api.lock.DistributedLock;

/** Keeps a key's lock alive while it has holders or waiters, including reentrant acquisitions. */
final class LocalLockRegistry<K> {

    private final ConcurrentHashMap<K, Entry> entries = new ConcurrentHashMap<>();

    private static final class Entry {

        private final ReentrantLock lock = new ReentrantLock();

        private int users;
    }

    private Entry retain(K key) {
        return entries.compute(key, (_, current) -> {
            Entry entry = current == null ? new Entry() : current;
            entry.users++;
            return entry;
        });
    }

    void lock(K key) {
        retain(key).lock.lock();
    }

    void unlock(K key) {
        entries.compute(key, (_, entry) -> {
            if (entry == null) {
                throw new IllegalMonitorStateException();
            }
            entry.lock.unlock();
            return --entry.users == 0 ? null : entry;
        });
    }

    private boolean tryLock(K key, Duration timeout) {
        long millis = timeout.toMillis();
        Entry entry = retain(key);
        boolean acquired = false;
        try {
            acquired = entry.lock.tryLock(millis, TimeUnit.MILLISECONDS);
            return acquired;
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
        finally {
            if (!acquired) {
                entries.computeIfPresent(key, (_, current) -> --current.users == 0 ? null : current);
            }
        }
    }

    DistributedLock namedLock(K key) {
        return new DistributedLock() {

            @Override
            public void lock() {
                LocalLockRegistry.this.lock(key);
            }

            @Override
            public boolean tryLock(Duration timeout) {
                return LocalLockRegistry.this.tryLock(key, timeout);
            }

            @Override
            public void unlock() {
                LocalLockRegistry.this.unlock(key);
            }
        };
    }
}
