package de.tum.cit.aet.artemis.programming.service.localci.distributed.api.map.listener;

public record MapEntryAddedEvent<K, V>(K key, V value) {

}
