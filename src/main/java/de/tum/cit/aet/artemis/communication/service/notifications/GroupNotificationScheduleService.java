package de.tum.cit.aet.artemis.communication.service.notifications;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;

import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.core.service.messaging.InstanceMessageSendService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.service.ExerciseDateService;

@Profile(PROFILE_CORE)
@Service
public class GroupNotificationScheduleService {

    private final SingleUserNotificationService singleUserNotificationService;

    private final InstanceMessageSendService instanceMessageSendService;

    private final GroupNotificationService groupNotificationService;

    public GroupNotificationScheduleService(SingleUserNotificationService singleUserNotificationService, InstanceMessageSendService instanceMessageSendService,
            GroupNotificationService groupNotificationService) {
        this.singleUserNotificationService = singleUserNotificationService;
        this.instanceMessageSendService = instanceMessageSendService;
        this.groupNotificationService = groupNotificationService;
    }

    /**
     * Auxiliary method that checks and creates appropriate notifications about exercise updates or updates the scheduled exercise-released notification
     *
     * @param exerciseBeforeUpdate is the initial exercise before it gets updated
     * @param exerciseAfterUpdate  is the updated exercise (needed to check potential difference in release date)
     * @param notificationText     holds the custom change message for the notification process
     */
    public void checkAndCreateAppropriateNotificationsWhenUpdatingExercise(Exercise exerciseBeforeUpdate, Exercise exerciseAfterUpdate, String notificationText) {

        // send exercise update notification
        groupNotificationService.notifyAboutExerciseUpdate(exerciseAfterUpdate, notificationText);

        // handle and check exercise released notification
        checkAndCreateExerciseReleasedNotificationsWhenUpdatingExercise(exerciseBeforeUpdate, exerciseAfterUpdate);

        // handle and check assessed exercise submission notification
        checkAndCreateAssessedExerciseSubmissionNotificationsWhenUpdatingExercise(exerciseBeforeUpdate, exerciseAfterUpdate);
    }

    /**
     * Auxiliary method that checks and creates exercise-released notifications when updating an exercise
     *
     * @param exerciseBeforeUpdate is the initial exercise before it gets updated
     * @param exerciseAfterUpdate  is the updated exercise (needed to check potential difference in release date)
     */
    private void checkAndCreateExerciseReleasedNotificationsWhenUpdatingExercise(Exercise exerciseBeforeUpdate, Exercise exerciseAfterUpdate) {
        final ZonedDateTime initialReleaseDate = exerciseBeforeUpdate.getReleaseDate();
        final ZonedDateTime updatedReleaseDate = exerciseAfterUpdate.getReleaseDate();
        ZonedDateTime timeNow = ZonedDateTime.now();

        boolean shouldNotifyAboutRelease = false;

        boolean isInitialReleaseDateUndefined = initialReleaseDate == null;
        boolean isInitialReleaseDateInThePast = false;
        boolean isInitialReleaseDateNow = false;
        boolean isInitialReleaseDateInTheFuture = false;

        boolean isUpdatedReleaseDateUndefined = updatedReleaseDate == null;
        boolean isUpdatedReleaseDateInThePast = false;
        boolean isUpdatedReleaseDateNow = false;
        boolean isUpdatedReleaseDateInTheFuture = false;

        if (!isInitialReleaseDateUndefined) {
            isInitialReleaseDateInThePast = initialReleaseDate.isBefore(timeNow);
            // with buffer of 1 minute
            isInitialReleaseDateNow = !initialReleaseDate.isBefore(timeNow.minusMinutes(1)) && !initialReleaseDate.isAfter(timeNow.plusMinutes(1));
            isInitialReleaseDateInTheFuture = initialReleaseDate.isAfter(timeNow);
        }

        if (!isUpdatedReleaseDateUndefined) {
            isUpdatedReleaseDateInThePast = updatedReleaseDate.isBefore(timeNow);
            // with buffer of 1 minute
            isUpdatedReleaseDateNow = !updatedReleaseDate.isBefore(timeNow.minusMinutes(1)) && !updatedReleaseDate.isAfter(timeNow.plusMinutes(1));
            isUpdatedReleaseDateInTheFuture = updatedReleaseDate.isAfter(timeNow);
        }

        // "decision matrix" based on initial and updated release date to decide if a release notification has to be sent out now, scheduled, or not

        // if the initial release date is (undefined/past/now) only send a notification if the updated date is in the future
        if (isInitialReleaseDateUndefined || isInitialReleaseDateInThePast || isInitialReleaseDateNow) {
            if (isUpdatedReleaseDateUndefined || isUpdatedReleaseDateInThePast || isUpdatedReleaseDateNow) {
                return;
            }
            else if (isUpdatedReleaseDateInTheFuture) {
                shouldNotifyAboutRelease = true;
            }
        }
        // no change in the release date
        else if (!isUpdatedReleaseDateUndefined && initialReleaseDate.isEqual(updatedReleaseDate)) {
            return;
        }
        // if the initial release date was in the future any other combination (-> undefined/now/past) will lead to an immediate release notification or a scheduled one (future)
        else if (isInitialReleaseDateInTheFuture) {
            shouldNotifyAboutRelease = true;
        }

        if (shouldNotifyAboutRelease) {
            checkNotificationForExerciseRelease(exerciseAfterUpdate);
        }
    }

    /**
     * Auxiliary method that checks and creates exercise-released notifications when updating an exercise
     *
     * @param exerciseBeforeUpdate is the initial exercise before it gets updated
     * @param exerciseAfterUpdate  is the updated exercise (needed to check potential difference in release date)
     */
    private void checkAndCreateAssessedExerciseSubmissionNotificationsWhenUpdatingExercise(Exercise exerciseBeforeUpdate, Exercise exerciseAfterUpdate) {
        final ZonedDateTime initialAssessmentDueDate = exerciseBeforeUpdate.getAssessmentDueDate();
        final ZonedDateTime updatedAssessmentDueDate = exerciseAfterUpdate.getAssessmentDueDate();
        ZonedDateTime timeNow = ZonedDateTime.now();

        // "decision matrix" based on initial and updated release date to decide if a notification has to be sent out now, scheduled, or not
        if (initialAssessmentDueDate != null && initialAssessmentDueDate.isAfter(timeNow)) {
            if (updatedAssessmentDueDate != null && updatedAssessmentDueDate.isAfter(timeNow)) {
                if (!initialAssessmentDueDate.isEqual(updatedAssessmentDueDate)) {
                    instanceMessageSendService.sendAssessedExerciseSubmissionNotificationSchedule(exerciseAfterUpdate.getId());
                }
                return;
            }
            singleUserNotificationService.notifyUsersAboutAssessedExerciseSubmission(exerciseAfterUpdate);
            return;
        }
        if (updatedAssessmentDueDate != null && updatedAssessmentDueDate.isAfter(timeNow)) {
            instanceMessageSendService.sendAssessedExerciseSubmissionNotificationSchedule(exerciseAfterUpdate.getId());
        }
    }

    /**
     * Auxiliary method that calls two other methods to check (create/schedule) notifications when a new exercise has been created
     * E.g. ExerciseReleased notification or AssessedExerciseSubmission notification
     *
     * @param exercise that is created
     */
    @Async
    public void checkNotificationsForNewExerciseAsync(Exercise exercise) {
        SecurityUtils.setAuthorizationObject(); // required for async
        // TODO: in a course with 2000 participants, this can take really long, we should optimize this
        checkNotificationForExerciseRelease(exercise);
        checkNotificationForAssessmentDueDate(exercise);
    }

    /**
     * Checks if a new exercise-released notification has to be created or even scheduled
     * The exercise update might have changed the release date, so the scheduled notification that informs the users about the release of this exercise has to be updated
     *
     * @param exercise that is created or updated
     */
    private void checkNotificationForExerciseRelease(Exercise exercise) {
        // Only notify students and tutors when the exercise is created for a course
        if (exercise.isCourseExercise()) {
            if (exercise.getReleaseDate() == null || !exercise.getReleaseDate().isAfter(ZonedDateTime.now())) {
                groupNotificationService.notifyAllGroupsAboutReleasedExercise(exercise);
            }
            else {
                instanceMessageSendService.sendExerciseReleaseNotificationSchedule(exercise.getId());
            }
        }
    }

    /**
     * Checks if a new AssessedExerciseSubmission notification has to be created or even scheduled
     * Used when a new exercise is created.
     *
     * @param exercise that is created
     */
    private void checkNotificationForAssessmentDueDate(Exercise exercise) {
        if (exercise.isCourseExercise() && !ExerciseDateService.isAfterAssessmentDueDate(exercise)) {
            instanceMessageSendService.sendAssessedExerciseSubmissionNotificationSchedule(exercise.getId());
        }
    }
}
