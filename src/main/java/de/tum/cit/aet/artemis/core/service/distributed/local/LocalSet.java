package de.tum.cit.aet.artemis.core.service.distributed.local;

import java.util.concurrent.ConcurrentHashMap;

import de.tum.cit.aet.artemis.core.service.distributed.api.set.AbstractDistributedSet;

public class LocalSet<T> extends AbstractDistributedSet<T> {

    public LocalSet() {
        super(ConcurrentHashMap.newKeySet());
    }
}
