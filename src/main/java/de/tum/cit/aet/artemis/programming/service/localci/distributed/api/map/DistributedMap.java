package de.tum.cit.aet.artemis.programming.service.localci.distributed.api.map;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.map.listener.MapEntryListener;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.map.listener.MapListener;

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
     * Unlocks the specified key in the map
     *
     * @param key the key to unlock
     */
    void unlock(K key);

    /**
     * Adds a listener that will be notified of changes to the map.
     * The listener methods get the affected entries passed as parameter.
     *
     * @param listener the listener to add
     * @return a unique identifier for the listener, which can be used to remove it later
     */
    UUID addEntryListener(MapEntryListener<K, V> listener);

    /**
     * Adds a listener that will be notified of changes to the map.
     * It is simplified version of listener that does not get the specific entries passed as parameter.
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
