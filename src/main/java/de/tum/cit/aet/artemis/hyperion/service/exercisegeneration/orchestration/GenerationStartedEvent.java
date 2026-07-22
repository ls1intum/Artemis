package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.hyperion.dto.GenerationMode;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

/**
 * Published by {@link GenerationJobService} once a generation job has been claimed, so {@link GenerationTaskService} can run it asynchronously off the request
 * thread. Using an event keeps the job service free of a dependency on the task service, which would otherwise close a construction cycle.
 *
 * @param jobId                    the claimed job id
 * @param user                     the requesting instructor
 * @param exercise                 the target exercise
 * @param userPrompt               the generation brief or the feedback to address
 * @param mode                     the explicit run intent (generate vs. adapt); carried on the job model so the engine can branch its seed and prompt without re-deriving it
 * @param expectedProblemStatement the problem statement as it was when the job was started; persistence refuses to overwrite later manual edits
 * @param expectedTitle            the title as it was when the job was started; persistence refuses to overwrite later manual edits
 * @param deadlineAt               the absolute admission-time deadline for this job, or {@code null} when disabled
 * @param budgetReservationId      the in-flight budget reservation to release when the async job finishes, or {@code null} when admission budgets are disabled
 * @param sourceBrief              the original instructor requirements that produced an AI draft, or {@code null} when the existing statement is instructor-authored
 */
public record GenerationStartedEvent(String jobId, User user, ProgrammingExercise exercise, String userPrompt, GenerationMode mode, String expectedProblemStatement,
        String expectedTitle, @Nullable Instant deadlineAt, @Nullable String budgetReservationId, @Nullable String sourceBrief) {

    public GenerationStartedEvent(String jobId, User user, ProgrammingExercise exercise, String userPrompt, GenerationMode mode) {
        this(jobId, user, exercise, userPrompt, mode, exercise.getProblemStatement(), exercise.getTitle(), null, null, null);
    }

    public GenerationStartedEvent(String jobId, User user, ProgrammingExercise exercise, String userPrompt, GenerationMode mode, String expectedProblemStatement,
            String expectedTitle, @Nullable Instant deadlineAt) {
        this(jobId, user, exercise, userPrompt, mode, expectedProblemStatement, expectedTitle, deadlineAt, null, null);
    }

    public GenerationStartedEvent(String jobId, User user, ProgrammingExercise exercise, String userPrompt, GenerationMode mode, String expectedProblemStatement,
            String expectedTitle, @Nullable Instant deadlineAt, @Nullable String budgetReservationId) {
        this(jobId, user, exercise, userPrompt, mode, expectedProblemStatement, expectedTitle, deadlineAt, budgetReservationId, null);
    }
}
