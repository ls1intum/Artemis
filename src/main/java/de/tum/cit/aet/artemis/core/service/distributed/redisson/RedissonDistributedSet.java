package de.tum.cit.aet.artemis.core.service.distributed.redisson;

import java.util.HashSet;
import java.util.Set;

import org.redisson.api.RSet;

import de.tum.cit.aet.artemis.core.service.distributed.api.set.AbstractDistributedSet;

public class RedissonDistributedSet<T> extends AbstractDistributedSet<T> {

    private final RSet<T> redisSet;

    public RedissonDistributedSet(RSet<T> set) {
        super(set);
        this.redisSet = set;
    }

    @Override
    public Set<T> getAll() {
        return new HashSet<>(redisSet.readAll());
    }

    @Override
    public void clear() {
        redisSet.delete();
    }
}
