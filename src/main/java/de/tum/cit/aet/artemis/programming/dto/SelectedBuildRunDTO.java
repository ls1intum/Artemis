package de.tum.cit.aet.artemis.programming.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Names one run that queues the builds of selected participations, so that a client can tell its own run from the runs of other
 * instructors of the same exercise.
 * <p>
 * The server returns it when it accepts the run and sends it again, as the payload of the completion message, once the run has queued
 * its last build.
 *
 * @param runId identifies the run, unique for every run
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SelectedBuildRunDTO(String runId) {
}
