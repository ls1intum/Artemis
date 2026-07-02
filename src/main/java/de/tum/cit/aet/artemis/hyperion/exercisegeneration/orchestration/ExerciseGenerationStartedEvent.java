package de.tum.cit.aet.artemis.hyperion.exercisegeneration.orchestration;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.hyperion.dto.GenerationMode;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

/**
 * Published by {@link ExerciseGenerationJobService} once a generation job has been claimed, so {@link ExerciseGenerationTaskService} can run it asynchronously off the request
 * thread. Using an event keeps the job service free of a dependency on the task service, which would otherwise close a construction cycle.
 *
 * @param jobId      the claimed job id
 * @param user       the requesting instructor
 * @param exercise   the target exercise
 * @param userPrompt the generation brief or the feedback to address
 * @param mode       the explicit run intent (generate vs. adapt); carried on the job model so the engine can branch its seed and prompt without re-deriving it
 */
public record ExerciseGenerationStartedEvent(String jobId, User user, ProgrammingExercise exercise, String userPrompt, GenerationMode mode) {
}
