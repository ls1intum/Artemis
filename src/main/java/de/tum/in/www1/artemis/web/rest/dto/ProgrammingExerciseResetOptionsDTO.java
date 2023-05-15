package de.tum.in.www1.artemis.web.rest.dto;

/**
 * This is a dto for the programming exercise reset options.
 */
public record ProgrammingExerciseResetOptionsDTO(boolean deleteBuildPlans, boolean deleteRepositories, boolean deleteParticipationsSubmissionsAndResults,
        boolean recreateBuildPlans) {
}
