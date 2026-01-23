package de.tum.cit.aet.artemis.programming.service.localci.distributed.api.set.listener;

import java.io.Serial;
import java.io.Serializable;

import org.jspecify.annotations.NonNull;

/**
 * Event representing an item being removed from a distributed set.
 *
 * @param <E>  the type of the item
 * @param item the item that was removed
 */
public record SetItemRemovedEvent<E>(@NonNull E item) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
}
