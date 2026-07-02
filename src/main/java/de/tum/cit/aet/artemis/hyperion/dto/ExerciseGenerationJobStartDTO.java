package de.tum.cit.aet.artemis.hyperion.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Response returned when an agentic whole-exercise generation run is started: the id of the job whose progress the client then follows over the websocket.
 *
 * @param jobId the started job id (also the websocket topic suffix)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseGenerationJobStartDTO(String jobId) {
}
