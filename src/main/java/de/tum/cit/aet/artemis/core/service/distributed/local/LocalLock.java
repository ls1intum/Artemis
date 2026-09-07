package de.tum.cit.aet.artemis.core.service.distributed.local;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import de.tum.cit.aet.artemis.core.service.distributed.api.lock.DistributedLock;

public class LocalLock implements DistributedLock {

    private final ReentrantLock lock = new ReentrantLock();

    @Override
    public void lock() {
        lock.lock();
    }

    @Override
    public boolean tryLock(Duration timeout) {
        try {
            return lock.tryLock(timeout.toMillis(), TimeUnit.MILLISECONDS);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @Override
    public void unlock() {
        lock.unlock();
    }
}
