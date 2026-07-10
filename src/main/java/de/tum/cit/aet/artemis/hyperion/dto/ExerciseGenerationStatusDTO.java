package de.tum.cit.aet.artemis.hyperion.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The reconnection view of an exercise-generation run, returned when a client (re)loads the page so it can replay the transcript and decide whether to keep listening.
 *
 * @param jobId           the job id (the websocket topic suffix)
 * @param running         whether the run is still active; when {@code true} the client should subscribe to the websocket to keep receiving live events
 * @param mode            the explicit run intent (generate vs. adapt), so a reconnecting client can restore the correct header label and the revert affordance without inferring it
 * @param events          the events produced so far, oldest first, to replay into the transcript
 * @param fileSnapshots   the latest whole-file snapshot per file written so far, in write order, so a reloading client can rehydrate the live editor preview and resume the stream
 * @param revertAvailable whether the server still retains the baseline required to undo the latest adaptation
 */
public record ExerciseGenerationStatusDTO(String jobId, boolean running, @JsonInclude(JsonInclude.Include.NON_NULL) GenerationMode mode, List<ExerciseGenerationEventDTO> events,
        List<ExerciseGenerationFileSnapshotDTO> fileSnapshots, boolean revertAvailable) {
}
