package de.tum.cit.aet.artemis.core.service.distributed.hazelcast;

import com.hazelcast.collection.ISet;

import de.tum.cit.aet.artemis.core.service.distributed.api.set.AbstractDistributedSet;

public class HazelcastDistributedSet<T> extends AbstractDistributedSet<T> {

    public HazelcastDistributedSet(ISet<T> set) {
        super(set);
    }
}
