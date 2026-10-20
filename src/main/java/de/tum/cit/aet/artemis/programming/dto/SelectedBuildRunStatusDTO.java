package de.tum.cit.aet.artemis.programming.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Whether a run that queues the builds of selected participations has queued its last build, for a client that missed the message
 * that announces it, for example because its websocket connection was down at that moment.
 * <p>
 * A run that is not known, because it is still going on, never existed for the exercise or ended so long ago that the server forgot it,
 * is not completed.
 *
 * @param completed whether the run has queued its last build
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SelectedBuildRunStatusDTO(boolean completed) {
}
