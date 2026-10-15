package de.tum.cit.aet.artemis.core.service.distributed.hazelcast;

import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.hazelcast.core.EntryEvent;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.HazelcastInstanceAware;
import com.hazelcast.map.EntryProcessor;
import com.hazelcast.splitbrainprotection.SplitBrainProtectionException;
import com.hazelcast.map.ExtendedMapEntry;
import com.hazelcast.map.IMap;
import com.hazelcast.map.listener.EntryAddedListener;
import com.hazelcast.map.listener.EntryRemovedListener;
import com.hazelcast.map.listener.EntryUpdatedListener;

import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.listener.MapEntryAddedEvent;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.listener.MapEntryListener;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.listener.MapEntryRemovedEvent;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.listener.MapEntryUpdatedEvent;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.listener.MapListener;

public class HazelcastDistributedMap<K, V> implements DistributedMap<K, V> {

    private final IMap<K, V> map;

    private final boolean ownerChecked;

    public HazelcastDistributedMap(IMap<K, V> map) {
        this(map, false);
    }

    public HazelcastDistributedMap(IMap<K, V> map, boolean ownerChecked) {
        this.map = map;
        this.ownerChecked = ownerChecked;
    }

    private enum Mutation {
        GET, PUT, PUT_IF_ABSENT, REMOVE, REMOVE_EXPECTED, REPLACE_EXPECTED
    }

    /** Checks current membership on the partition owner, not the asynchronously updated protection cache. */
    private static final class ProtectedEntryProcessor<K, V> implements EntryProcessor<K, V, Object>, HazelcastInstanceAware {

        private final String mapName;

        private final Mutation mutation;

        private final V expected;

        private final V replacement;

        private transient HazelcastInstance instance;

        private ProtectedEntryProcessor(String mapName, Mutation mutation, V expected, V replacement) {
            this.mapName = mapName;
            this.mutation = mutation;
            this.expected = expected;
            this.replacement = replacement;
        }

        @Override
        public void setHazelcastInstance(HazelcastInstance instance) {
            this.instance = instance;
        }

        @Override
        public Object process(Map.Entry<K, V> entry) {
            String policy = instance.getConfig().findMapConfig(mapName).getSplitBrainProtectionName();
            var protection = instance.getConfig().getSplitBrainProtectionConfig(policy);
            if (!protection.getFunctionImplementation().apply(instance.getCluster().getMembers())) {
                throw new SplitBrainProtectionException("Current membership does not satisfy " + policy);
            }
            V current = entry.getValue();
            return switch (mutation) {
                case GET -> current;
                case PUT -> {
                    entry.setValue(replacement);
                    yield current;
                }
                case PUT_IF_ABSENT -> {
                    if (current == null) {
                        entry.setValue(replacement);
                    }
                    yield current;
                }
                case REMOVE -> {
                    entry.setValue(null);
                    yield current;
                }
                case REMOVE_EXPECTED, REPLACE_EXPECTED -> {
                    boolean matches = current != null && Objects.equals(current, expected);
                    if (matches) {
                        entry.setValue(mutation == Mutation.REMOVE_EXPECTED ? null : replacement);
                    }
                    yield matches;
                }
            };
        }
    }

    @SuppressWarnings("unchecked")
    private <R> R protectedOperation(K key, Mutation mutation, V expected, V replacement) {
        return (R) map.executeOnKey(key, new ProtectedEntryProcessor<>(map.getName(), mutation, expected, replacement));
    }

    public V get(K key) {
        return ownerChecked ? protectedOperation(key, Mutation.GET, null, null) : map.get(key);
    }

    @Override
    public Map<K, V> getAll(Set<K> keys) {
        return map.getAll(keys);
    }

    @Override
    public void put(K key, V value) {
        if (ownerChecked) {
            protectedOperation(key, Mutation.PUT, null, value);
        }
        else {
            map.put(key, value);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * Hazelcast supports a per-entry TTL directly on {@code IMap}, which also works from a client, unlike a map-level
     * TTL that would require a {@code MapConfig} registered before the proxy exists.
     */
    @Override
    public void put(K key, V value, Duration timeToLive) {
        map.put(key, value, timeToLive.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public V putIfAbsent(K key, V value) {
        return ownerChecked ? protectedOperation(key, Mutation.PUT_IF_ABSENT, null, value) : map.putIfAbsent(key, value);
    }

    @Override
    public V putIfAbsent(K key, V value, Duration timeToLive) {
        return map.putIfAbsent(key, value, timeToLive.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public boolean remove(K key, V value) {
        return ownerChecked ? protectedOperation(key, Mutation.REMOVE_EXPECTED, value, null) : map.remove(key, value);
    }

    @Override
    public boolean replace(K key, V expectedValue, V replacementValue) {
        if (ownerChecked) {
            return protectedOperation(key, Mutation.REPLACE_EXPECTED, expectedValue, replacementValue);
        }
        return map.executeOnKey(key, entry -> {
            if (entry.getValue() == null || !Objects.equals(entry.getValue(), expectedValue)) {
                return false;
            }
            ((ExtendedMapEntry<K, V>) entry).setValueWithoutChangingExpiryTime(replacementValue);
            return true;
        });
    }

    @Override
    public boolean refreshTimeToLive(K key, Duration timeToLive) {
        return map.setTtl(key, timeToLive.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public V remove(K key) {
        return ownerChecked ? protectedOperation(key, Mutation.REMOVE, null, null) : map.remove(key);
    }

    @Override
    public Collection<V> values() {
        return map.values();
    }

    @Override
    public Set<K> keySet() {
        return map.keySet();
    }

    @Override
    public Set<Map.Entry<K, V>> entrySet() {
        return map.entrySet();
    }

    @Override
    public HashMap<K, V> getMapCopy() {
        return new HashMap<>(map);
    }

    @Override
    public int size() {
        return map.size();
    }

    @Override
    public void clear() {
        map.clear();
    }

    @Override
    public void lock(K key) {
        map.lock(key);
    }

    @Override
    public void unlock(K key) {
        map.unlock(key);
    }

    @Override
    public UUID addEntryListener(MapEntryListener<K, V> listener) {
        class HazelcastMapEntryListener implements EntryAddedListener<K, V>, EntryRemovedListener<K, V>, EntryUpdatedListener<K, V> {

            @Override
            public void entryAdded(EntryEvent<K, V> event) {
                listener.entryAdded(new MapEntryAddedEvent<>(event.getKey(), event.getValue()));
            }

            @Override
            public void entryRemoved(EntryEvent<K, V> event) {
                listener.entryRemoved(new MapEntryRemovedEvent<>(event.getKey(), event.getOldValue()));
            }

            @Override
            public void entryUpdated(EntryEvent<K, V> event) {
                listener.entryUpdated(new MapEntryUpdatedEvent<>(event.getKey(), event.getValue(), event.getOldValue()));
            }
        }
        return map.addEntryListener(new HazelcastMapEntryListener(), true);
    }

    @Override
    public UUID addListener(MapListener listener) {
        class HazelcastMapEntryListener implements EntryAddedListener<K, V>, EntryRemovedListener<K, V>, EntryUpdatedListener<K, V> {

            @Override
            public void entryAdded(EntryEvent<K, V> event) {
                listener.entryAdded();
            }

            @Override
            public void entryRemoved(EntryEvent<K, V> event) {
                listener.entryRemoved();
            }

            @Override
            public void entryUpdated(EntryEvent<K, V> event) {
                listener.entryUpdated();
            }
        }
        return map.addEntryListener(new HazelcastMapEntryListener(), false);
    }

    @Override
    public void removeListener(UUID registrationId) {
        map.removeEntryListener(registrationId);
    }
}
