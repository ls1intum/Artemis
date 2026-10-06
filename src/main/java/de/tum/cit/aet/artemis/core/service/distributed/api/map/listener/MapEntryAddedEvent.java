package de.tum.cit.aet.artemis.core.service.distributed.api.map.listener;

import java.io.Serial;
import java.io.Serializable;

import org.jspecify.annotations.NonNull;

public record MapEntryAddedEvent<K, V>(@NonNull K key, @NonNull V value) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
}
