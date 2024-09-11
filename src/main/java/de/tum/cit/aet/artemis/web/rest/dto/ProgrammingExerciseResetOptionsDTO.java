package de.tum.cit.aet.artemis.web.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * This is a dto for the programming exercise reset options.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ProgrammingExerciseResetOptionsDTO(boolean deleteBuildPlans, boolean deleteRepositories, boolean deleteParticipationsSubmissionsAndResults,
        boolean recreateBuildPlans) {
}
