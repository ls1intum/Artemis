package de.tum.cit.aet.artemis.programming.service.localci.distributedData.api.map.listener;

public record MapEntryUpdatedEvent<K, V>(K key, V value, V oldValue) {

}
