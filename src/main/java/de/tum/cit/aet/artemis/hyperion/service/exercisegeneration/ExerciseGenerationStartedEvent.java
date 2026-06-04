package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

/**
 * Published by {@link ExerciseGenerationJobService} once a generation job has been claimed, so {@link ExerciseGenerationTaskService} can run it asynchronously off the request
 * thread. Using an event keeps the job service free of a dependency on the task service, which would otherwise close a construction cycle.
 *
 * @param jobId      the claimed job id
 * @param user       the requesting instructor
 * @param exercise   the target exercise
 * @param userPrompt the generation brief or the feedback to address
 */
public record ExerciseGenerationStartedEvent(String jobId, User user, ProgrammingExercise exercise, String userPrompt) {
}
