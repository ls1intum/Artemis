package de.tum.cit.aet.artemis.core.service.distributed.redisson;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.redisson.api.RLock;

import de.tum.cit.aet.artemis.core.service.distributed.api.lock.DistributedLock;

/** Adapts a Redisson reentrant lock to the Artemis provider contract. */
public class RedissonDistributedLock implements DistributedLock {

    private final RLock lock;

    public RedissonDistributedLock(RLock lock) {
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
        catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @Override
    public void unlock() {
        lock.unlock();
    }
}
