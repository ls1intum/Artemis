package de.tum.cit.aet.artemis.core.service.distributed.local;

import java.util.concurrent.locks.ReentrantLock;

import de.tum.cit.aet.artemis.core.service.distributed.api.lock.AbstractDistributedLock;

public class LocalLock extends AbstractDistributedLock {

    public LocalLock() {
        super(new ReentrantLock());
    }
}
