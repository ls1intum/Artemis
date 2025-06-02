package de.tum.cit.aet.artemis.programming.service.localci.distributedData.api.map.listener;

public record MapEntryEvent<K, V>(K key, V value, V oldValue) {

}
