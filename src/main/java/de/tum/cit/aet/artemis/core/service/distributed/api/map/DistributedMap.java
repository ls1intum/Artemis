package de.tum.cit.aet.artemis.core.service.distributed.api.map;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import de.tum.cit.aet.artemis.core.service.distributed.api.map.listener.MapEntryListener;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.listener.MapListener;

public interface DistributedMap<K, V> {

    /**
     * Retrieves the value associated with the specified key.
     *
     * @param key the key whose associated value is to be returned
     * @return the value associated with the specified key, or {@code null} if no mapping exists
     */
    V get(K key);

    /**
     * Retrieves all key-value pairs for the specified keys.
     *
     * @param keys the set of keys whose associated values are to be returned
     * @return a map containing the key-value pairs for the specified keys
     */
    Map<K, V> getAll(Set<K> keys);

    /**
     * Associates the specified value with the specified key in this map.
     *
     * @param key   the key with which the specified value is to be associated
     * @param value the value to be associated with the specified key
     */
    void put(K key, V value);

    /**
     * Stores an entry that is removed automatically once the given time-to-live has elapsed.
     *
     * <p>
     * Only supported on maps obtained from
     * {@link de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider#getExpiringMap(String, Duration)}.
     * Expiry is enforced on read as well as by the backend's background eviction, so a read after the time-to-live has
     * elapsed never observes the entry, regardless of how coarse the backend's eviction interval is.
     *
     * @param key        the key
     * @param value      the value
     * @param timeToLive how long the entry remains readable
     * @throws UnsupportedOperationException if this map was not created as an expiring map
     */
    void put(K key, V value, Duration timeToLive);

    /**
     * Atomically stores the value only if the key is not already mapped.
     *
     * <p>
     * On an expiring map the entry receives the map's default time-to-live, so a claim made through this method cannot
     * outlive the map's configured lifetime.
     *
     * @param key   the key
     * @param value the value to store if absent
     * @return the existing value, or {@code null} if the value was stored
     */
    V putIfAbsent(K key, V value);

    /**
     * Atomically stores the value with an explicit time-to-live only if the key is not already mapped.
     *
     * @param key        the key
     * @param value      the value to store if absent
     * @param timeToLive how long the entry remains readable
     * @return the existing value, or {@code null} if the value was stored
     * @throws UnsupportedOperationException if this map was not created as an expiring map
     */
    V putIfAbsent(K key, V value, Duration timeToLive);

    /**
     * Atomically removes the entry only if it currently maps to the given value.
     *
     * <p>
     * Used to avoid clobbering an entry that another node replaced between a read and the removal.
     *
     * @param key   the key
     * @param value the value the entry must currently hold
     * @return true if the entry was removed
     */
    boolean remove(K key, V value);

    /**
     * Atomically replaces an entry only if it still has the expected value.
     *
     * @param key              the key
     * @param expectedValue    the value the entry must currently hold
     * @param replacementValue the new value
     * @return true if the value was replaced
     */
    boolean replace(K key, V expectedValue, V replacementValue);

    /**
     * Atomically renews the expiry of an existing entry without changing its value.
     *
     * @param key        the key
     * @param timeToLive the new time to live
     * @return true if the entry existed and its expiry was renewed
     * @throws UnsupportedOperationException if this map was not created as an expiring map
     */
    boolean refreshTimeToLive(K key, Duration timeToLive);

    /**
     * Removes the mapping for the specified key from this map if present.
     *
     * @param key the key whose mapping is to be removed
     * @return the previous value associated with the specified key, or {@code null} if there was no mapping
     */
    V remove(K key);

    /**
     * Get a collection of all values in this map.
     *
     * @return a collection of all values in this map
     */
    Collection<V> values();

    /**
     * Get a set of all keys in this map.
     *
     * @return a set of all keys in this map
     */
    Set<K> keySet();

    /**
     * Get a set of all entries in this map.
     *
     * @return a set of all entries in this map
     */
    Set<Map.Entry<K, V>> entrySet();

    /**
     * Returns a copy of the map as a HashMap.
     *
     * @return a HashMap containing all entries in this map
     */
    Map<K, V> getMapCopy();

    /**
     * Get the number of key-value pairs in this map.
     *
     * @return the number of key-value pairs in this map
     */
    int size();

    /**
     * Stores every entry of the given map.
     *
     * <p>
     * Not atomic as a whole: each entry is stored individually, so a concurrent reader can observe a partially applied
     * batch. That matches how the backends behave for bulk writes and is acceptable for the current callers, which write
     * independent keys.
     *
     * @param entries the entries to store
     */
    default void putAll(Map<? extends K, ? extends V> entries) {
        entries.forEach(this::put);
    }

    /**
     * @param key the key to look for
     * @return true if the map holds an entry for the key
     */
    default boolean containsKey(K key) {
        // Distributed maps do not permit null values, so a non-null get is equivalent to presence.
        return get(key) != null;
    }

    /**
     * @return true if the map holds no entries
     */
    default boolean isEmpty() {
        return size() == 0;
    }

    /**
     * Returns the value for the key, computing and storing it if absent.
     *
     * <p>
     * Implemented on top of {@link #putIfAbsent(Object, Object)} rather than delegating to a backend primitive, because
     * the backends run the mapping function on the calling node anyway. If two nodes compute concurrently, exactly one
     * stored value wins and both callers observe that same winner, so the result is consistent even though the function
     * may run more than once. The function must therefore be side-effect free.
     *
     * @param key             the key
     * @param mappingFunction computes the value when the key is absent
     * @return the existing or newly stored value, or {@code null} if the function returned {@code null}
     */
    default V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
        V existing = get(key);
        if (existing != null) {
            return existing;
        }
        V computed = mappingFunction.apply(key);
        if (computed == null) {
            return null;
        }
        V wonByOtherCaller = putIfAbsent(key, computed);
        return wonByOtherCaller != null ? wonByOtherCaller : computed;
    }

    /**
     * Clears the map, removing all key-value pairs.
     */
    void clear();

    /**
     * Locks the specified key in the map
     *
     * @param key the key to lock
     */
    void lock(K key);

    /**
     * Locks the key and releases the backend lock automatically after the lease elapses.
     *
     * @param key   key to lock
     * @param lease maximum lock lifetime
     */
    void lock(K key, Duration lease);

    /**
     * Unlocks the specified key in the map
     *
     * @param key the key to unlock
     */
    void unlock(K key);

    /**
     * Adds a listener that will be notified of changes to the map.
     * The listener methods get the affected entries passed as parameter.
     * Automatic expiry is not a map change notification: backends expose different expiry event models, and Redis
     * requires optional keyspace notifications. Callers that need expiry behavior must observe map state instead.
     *
     * @param listener the listener to add
     * @return a unique identifier for the listener, which can be used to remove it later
     */
    UUID addEntryListener(MapEntryListener<K, V> listener);

    /**
     * Adds a listener that will be notified of changes to the map.
     * It is simplified version of listener that does not get the specific entries passed as parameter.
     * Automatic expiry does not trigger this listener; callers that need expiry behavior must observe map state.
     *
     * @param listener the listener to add
     * @return a unique identifier for the listener, which can be used to remove it later
     */
    UUID addListener(MapListener listener);

    /**
     * Removes a previously registered listener.
     *
     * @param registrationId the id returned from addEntryListener/addListener
     */
    void removeListener(UUID registrationId);
}
