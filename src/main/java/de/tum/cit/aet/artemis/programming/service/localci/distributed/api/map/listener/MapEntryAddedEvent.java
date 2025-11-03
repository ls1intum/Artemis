package de.tum.cit.aet.artemis.programming.service.localci.distributed.api.map.listener;

import java.io.Serial;
import java.io.Serializable;

import jakarta.validation.constraints.NotNull;

public record MapEntryAddedEvent<K, V>(@NotNull K key, @NotNull V value) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
}
