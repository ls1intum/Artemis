package de.tum.cit.aet.artemis.programming.service.localci.distributed.api.map.listener;

public record MapEntryUpdatedEvent<K, V>(K key, V value, V oldValue) {

}
