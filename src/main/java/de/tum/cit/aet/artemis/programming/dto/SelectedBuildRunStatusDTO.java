package de.tum.cit.aet.artemis.programming.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Whether a run that queues the builds of selected participations is still going on, for a client that missed the message that announces
 * its end, for example because its websocket connection was down at that moment.
 * <p>
 * Only a run the server knows to be going on is running. A run that ended, never existed for the exercise, or that the server forgot after
 * a long time is not, so a client that waits for a run stops waiting after any time away.
 *
 * @param running whether the run is still queueing builds
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SelectedBuildRunStatusDTO(boolean running) {
}
