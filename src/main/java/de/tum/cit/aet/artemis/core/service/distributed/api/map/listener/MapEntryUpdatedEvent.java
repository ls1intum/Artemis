package de.tum.cit.aet.artemis.core.service.distributed.api.map.listener;

import java.io.Serial;
import java.io.Serializable;

import org.jspecify.annotations.NonNull;

public record MapEntryUpdatedEvent<K, V>(@NonNull K key, V value, @NonNull V oldValue) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
}
