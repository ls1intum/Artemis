package de.tum.cit.aet.artemis.iris.domain.message;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The kind of context transition recorded by a {@link IrisMessageSender#CTXSWAP} marker message.
 * <p>
 * Each constant serializes to its lowercase {@link JsonProperty} value in the marker's JSON content,
 * consumed by the Iris client (divider rendering) and the Pyris pipeline. Keep these values in sync
 * with {@code IrisContextSwitchTransition} on the client and {@code ContextSwitchTransition} in Pyris.
 */
public enum IrisContextSwitchTransition {

    /** A lecture/exercise context was added on top of the course-level chat. */
    @JsonProperty("added")
    ADDED,

    /** The lecture/exercise context was removed, returning to the course-level chat. */
    @JsonProperty("removed")
    REMOVED,

    /** One lecture/exercise context was swapped for another. */
    @JsonProperty("changed")
    CHANGED
}
