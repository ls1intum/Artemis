package de.tum.cit.aet.artemis.core.service.distributed.hazelcast;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import com.hazelcast.map.IMap;

import de.tum.cit.aet.artemis.core.service.distributed.api.lock.DistributedLock;

/**
 * Cluster-wide lock backed by a key lock on a dedicated Hazelcast map.
 *
 * <p>
 * Hazelcast's {@code FencedLock} would be the natural fit but it lives in the CP subsystem, which Artemis does not
 * enable and which requires at least three members. An {@code IMap} key lock is the available alternative and gives the
 * same AP semantics the Iris and Atlas schedulers already rely on.
 */
public class HazelcastDistributedLock implements DistributedLock {

    private final IMap<String, Boolean> lockMap;

    private final String name;

    public HazelcastDistributedLock(IMap<String, Boolean> lockMap, String name) {
        this.lockMap = lockMap;
        this.name = name;
    }

    @Override
    public void lock() {
        lockMap.lock(name);
    }

    @Override
    public boolean tryLock(Duration timeout) {
        try {
            return lockMap.tryLock(name, timeout.toMillis(), TimeUnit.MILLISECONDS);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @Override
    public void unlock() {
        lockMap.unlock(name);
    }
}
