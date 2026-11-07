package de.tum.cit.aet.artemis.exercise.util;

import static de.tum.cit.aet.artemis.core.config.ArtemisConstants.SPRING_PROFILE_TEST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.exercise.domain.EntityDeletionStatus;
import de.tum.cit.aet.artemis.exercise.domain.EntityDeletionType;
import de.tum.cit.aet.artemis.exercise.dto.EntityDeletionProgressDTO;
import de.tum.cit.aet.artemis.exercise.service.EntityDeletionProgressService;

/**
 * Waits for the deletion of an exercise, an exam or an exercise group, which the server runs in the background after the DELETE request returned 202 (Accepted). A test that
 * asserts what a deletion removed calls one of the {@code await...Deletion} methods after the request. These assert that the deletion completed and fail with the reason the
 * server reported otherwise, so a deletion that failed in the background cannot go unnoticed. A test that expects a failed deletion calls one of the
 * {@code await...DeletionFailure} methods instead.
 * <p>
 * The final status is only stored after the deletion released all its claims, so a test may start the next deletion of the same entity right after an await method returned.
 * The ids of entities are not reused within a test run, so the status of an earlier deletion cannot be mistaken for the one that a test waits for.
 */
@Lazy
@Service
@Profile(SPRING_PROFILE_TEST)
public class AsyncDeletionTestUtilService {

    private static final Duration TIMEOUT = Duration.ofSeconds(60);

    @Autowired
    private EntityDeletionProgressService entityDeletionProgressService;

    /**
     * Waits until the background deletion of the exercise ended and asserts that it completed.
     *
     * @param exerciseId the id of the exercise that is deleted
     * @return the last progress of the deletion, which is completed
     */
    public EntityDeletionProgressDTO awaitExerciseDeletion(long exerciseId) {
        return assertCompleted(awaitEnd(EntityDeletionProgressService.exerciseScopeKey(exerciseId), EntityDeletionType.EXERCISE, exerciseId));
    }

    /**
     * Waits until the background deletion of the exercise ended and asserts that it failed.
     *
     * @param exerciseId the id of the exercise that is deleted
     * @return the last progress of the deletion, which is failed and has an error message
     */
    public EntityDeletionProgressDTO awaitExerciseDeletionFailure(long exerciseId) {
        return assertFailed(awaitEnd(EntityDeletionProgressService.exerciseScopeKey(exerciseId), EntityDeletionType.EXERCISE, exerciseId));
    }

    /**
     * Waits until the background deletion of the exam ended and asserts that it completed.
     *
     * @param examId the id of the exam that is deleted
     * @return the last progress of the deletion, which is completed
     */
    public EntityDeletionProgressDTO awaitExamDeletion(long examId) {
        return assertCompleted(awaitEnd(EntityDeletionProgressService.examScopeKey(examId), EntityDeletionType.EXAM, examId));
    }

    /**
     * Waits until the background deletion of the exam ended and asserts that it failed.
     *
     * @param examId the id of the exam that is deleted
     * @return the last progress of the deletion, which is failed and has an error message
     */
    public EntityDeletionProgressDTO awaitExamDeletionFailure(long examId) {
        return assertFailed(awaitEnd(EntityDeletionProgressService.examScopeKey(examId), EntityDeletionType.EXAM, examId));
    }

    /**
     * Waits until the background deletion of the exercise group ended and asserts that it completed.
     *
     * @param examId          the id of the exam of the exercise group
     * @param exerciseGroupId the id of the exercise group that is deleted
     * @return the last progress of the deletion, which is completed
     */
    public EntityDeletionProgressDTO awaitExerciseGroupDeletion(long examId, long exerciseGroupId) {
        return assertCompleted(awaitEnd(EntityDeletionProgressService.examScopeKey(examId), EntityDeletionType.EXERCISE_GROUP, exerciseGroupId));
    }

    /**
     * Waits until the background deletion of the exercise group ended and asserts that it failed.
     *
     * @param examId          the id of the exam of the exercise group
     * @param exerciseGroupId the id of the exercise group that is deleted
     * @return the last progress of the deletion, which is failed and has an error message
     */
    public EntityDeletionProgressDTO awaitExerciseGroupDeletionFailure(long examId, long exerciseGroupId) {
        return assertFailed(awaitEnd(EntityDeletionProgressService.examScopeKey(examId), EntityDeletionType.EXERCISE_GROUP, exerciseGroupId));
    }

    private EntityDeletionProgressDTO awaitEnd(String scopeKey, EntityDeletionType entityType, long entityId) {
        // The status of an earlier deletion of the same scope stays available, for example of another exercise group of the exam, so the entity has to match.
        await().atMost(TIMEOUT).pollInterval(Duration.ofMillis(50)).until(() -> entityDeletionProgressService.getProgress(scopeKey)
                .filter(progress -> progress.entityType() == entityType && progress.entityId() == entityId && progress.status() != EntityDeletionStatus.IN_PROGRESS).isPresent());
        return entityDeletionProgressService.getProgress(scopeKey).orElseThrow();
    }

    private static EntityDeletionProgressDTO assertCompleted(EntityDeletionProgressDTO progress) {
        assertThat(progress.status()).as("the background deletion of %s %s ended with the error: %s", progress.entityType(), progress.entityId(), progress.errorMessage())
                .isEqualTo(EntityDeletionStatus.COMPLETED);
        return progress;
    }

    private static EntityDeletionProgressDTO assertFailed(EntityDeletionProgressDTO progress) {
        assertThat(progress.status()).as("the background deletion of %s %s was expected to fail", progress.entityType(), progress.entityId())
                .isEqualTo(EntityDeletionStatus.FAILED);
        assertThat(progress.errorMessage()).as("error message of the failed deletion").isNotBlank();
        return progress;
    }
}
