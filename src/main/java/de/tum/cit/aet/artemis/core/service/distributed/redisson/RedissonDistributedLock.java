package de.tum.cit.aet.artemis.core.service.distributed.redisson;

import org.redisson.api.RLock;

import de.tum.cit.aet.artemis.core.service.distributed.api.lock.AbstractDistributedLock;

public class RedissonDistributedLock extends AbstractDistributedLock {

    public RedissonDistributedLock(RLock lock) {
        super(lock);
    }
}
