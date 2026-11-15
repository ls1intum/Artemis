package de.tum.cit.aet.artemis.core.service.distributed.api.lock;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

/** Lock delegation with the interruption semantics shared by local and Redis locks. */
public abstract class AbstractDistributedLock implements DistributedLock {

    private final Lock lock;

    protected AbstractDistributedLock(Lock lock) {
        this.lock = lock;
    }

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
