package de.tum.cit.aet.artemis.programming.service.localci.distributed.api.map.listener;

public record MapEntryRemovedEvent<K, V>(K key, V oldValue) {

}
