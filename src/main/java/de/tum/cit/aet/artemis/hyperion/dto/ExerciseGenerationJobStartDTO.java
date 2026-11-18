package de.tum.cit.aet.artemis.hyperion.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response returned when an agentic whole-exercise generation run is started: the id of the job whose progress the client then follows over the websocket.
 *
 * @param exerciseId       destination exercise, including a newly created variant draft
 * @param sourceExerciseId source exercise, equal to the destination for in-place authoring
 * @param jobId            the started job id (also the websocket topic suffix)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseGenerationJobStartDTO(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String jobId, @Nullable Long exerciseId, @Nullable Long sourceExerciseId) {

    public ExerciseGenerationJobStartDTO(String jobId) {
        this(jobId, null, null);
    }
}
