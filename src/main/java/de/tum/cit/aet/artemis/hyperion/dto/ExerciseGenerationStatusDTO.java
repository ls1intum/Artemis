package de.tum.cit.aet.artemis.hyperion.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The reconnection view of an exercise-generation run, returned when a client (re)loads the page so it can replay the transcript and decide whether to keep listening.
 *
 * @param jobId   the job id (the websocket topic suffix)
 * @param running whether the run is still active; when {@code true} the client should subscribe to the websocket to keep receiving live events
 * @param events  the events produced so far, oldest first, to replay into the transcript
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExerciseGenerationStatusDTO(String jobId, boolean running, List<ExerciseGenerationEventDTO> events) {
}
