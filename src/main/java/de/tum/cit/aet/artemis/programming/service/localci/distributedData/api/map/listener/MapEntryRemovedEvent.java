package de.tum.cit.aet.artemis.programming.service.localci.distributedData.api.map.listener;

public record MapEntryRemovedEvent<K, V>(K key, V oldValue) {

}
