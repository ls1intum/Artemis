package de.tum.in.www1.artemis.service.scheduled;

import java.time.ZonedDateTime;
import java.util.*;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Submission;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseLifecycle;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.SubmissionRepository;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.notifications.GroupNotificationService;
import de.tum.in.www1.artemis.service.notifications.SingleUserNotificationService;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;
import tech.jhipster.config.JHipsterConstants;

@Service
@Profile("scheduling")
public class NotificationScheduleService {

    private final Logger log = LoggerFactory.getLogger(NotificationScheduleService.class);

    private final ScheduleService scheduleService;

    private final ExerciseRepository exerciseRepository;

    private final SubmissionRepository submissionRepository;

    private final Environment environment;

    private final GroupNotificationService groupNotificationService;

    private final SingleUserNotificationService singleUserNotificationService;

    public NotificationScheduleService(ScheduleService scheduleService, ExerciseRepository exerciseRepository, GroupNotificationService groupNotificationService,
            Environment environment, SubmissionRepository submissionRepository, SingleUserNotificationService singleUserNotificationService) {
        this.scheduleService = scheduleService;
        this.exerciseRepository = exerciseRepository;
        this.environment = environment;
        this.groupNotificationService = groupNotificationService;
        this.submissionRepository = submissionRepository;
        this.singleUserNotificationService = singleUserNotificationService;
    }

    /**
     * Schedules ongoing notification processes on server start up
     */
    @PostConstruct
    public void scheduleRunningNotificationProcessesOnStartup() {
        try {
            Collection<String> activeProfiles = Arrays.asList(environment.getActiveProfiles());
            if (activeProfiles.contains(JHipsterConstants.SPRING_PROFILE_DEVELOPMENT)) {
                // only execute this on production server, i.e. when the prod profile is active
                // NOTE: if you want to test this locally, please comment it out, but do not commit the changes
                return;
            }

            checkSecurityUtils();

            // EXERCISE_RELEASED
            Set<Exercise> exercisesToBeScheduled = exerciseRepository.findAllExercisesWithCurrentOrUpcomingReleaseDate(ZonedDateTime.now());
            exercisesToBeScheduled.forEach(this::scheduleNotificationForReleasedExercise);

            // EXERCISE_SUBMISSION_ASSESSED
            List<Submission> submissionsToBeScheduled = submissionRepository.findAllSubmittedAndRatedSubmissionsWithFutureOrCurrentAssessmentDueDate(ZonedDateTime.now());
            submissionsToBeScheduled.forEach(submission -> {
                Exercise foundExercise = submission.getParticipation().getExercise();
                scheduleNotificationForAssessedExercisesSubmissions(foundExercise, submission);
            });

            log.info("Scheduled {} released exercise notifications.", exercisesToBeScheduled.size());
        }
        catch (Exception exception) {
            log.error("Failed to start NotificationScheduleService", exception);
        }
    }

    /**
     * Checks the SecurityUtils for authentication, if not yet authenticated do so.
     */
    private void checkSecurityUtils() {
        if (!SecurityUtils.isAuthenticated()) {
            SecurityUtils.setAuthorizationObject();
        }
    }

    /// EXERCISE_RELEASED

    /**
     * updateScheduling method for the notificationType EXERCISE_RELEASED
     * @param exercise that should trigger a notification when it is released
     */
    public void updateSchedulingForReleasedExercises(Exercise exercise) {
        if (exercise.getReleaseDate() == null || ZonedDateTime.now().isAfter(exercise.getReleaseDate())) {
            // to avoid canceling more important tasks we simply return here.
            // to make sure no wrong notification is sent out the date is checked again in the concrete notification method
            return;
        }
        if (exercise.isCourseExercise()) {
            scheduleNotificationForReleasedExercise(exercise);
        }
    }

    /**
     * The place where the actual tasks/methods are called/scheduled that should be run at the exercise release time
     * @param exercise which will be announced by a notifications at release date
     */
    private void scheduleNotificationForReleasedExercise(Exercise exercise) {
        try {
            checkSecurityUtils();
            scheduleService.scheduleTask(exercise, ExerciseLifecycle.RELEASE, () -> {
                checkSecurityUtils();
                // if the exercise has been updated in the meantime the scheduled immutable exercise is outdated and has to be replaced by the current one in the DB
                Exercise foundCurrentVersionOfScheduledExercise;
                try {
                    foundCurrentVersionOfScheduledExercise = exerciseRepository.findByIdElseThrow(exercise.getId());
                }
                catch (EntityNotFoundException entityNotFoundException) {
                    log.debug("Exercise is no longer in the database " + exercise.getId());
                    return;
                }
                // only send a notification if ReleaseDate is defined and not in the future (i.e. in the range [now-2 minutes, now]) (due to possible delays in scheduling)
                ZonedDateTime releaseDate = foundCurrentVersionOfScheduledExercise.getReleaseDate();
                if (releaseDate != null && !releaseDate.isBefore(ZonedDateTime.now().minusMinutes(2)) && !releaseDate.isAfter(ZonedDateTime.now())) {
                    groupNotificationService.notifyAllGroupsAboutReleasedExercise(foundCurrentVersionOfScheduledExercise);
                }
            });
            log.debug("Scheduled notify about started exercise after due date for exercise '{}' (#{}) for {}.", exercise.getTitle(), exercise.getId(), exercise.getReleaseDate());
        }
        catch (Exception exception) {
            log.error("Failed to schedule notification for exercise " + exercise.getId(), exception);
        }
    }

    /// EXERCISE_SUBMISSION_ASSESSED

    /**
     * updateScheduling method for the notificationType EXERCISE_SUBMISSION_ASSESSED
     * @param submission that should trigger a notification when the assessment due date (of the respective exercise) is over
     */
    public void updateSchedulingForAssessedExercisesSubmissions(Submission submission) {
        StudentParticipation studentParticipation = (StudentParticipation) submission.getParticipation();
        long exerciseId = studentParticipation.getExercise().getId();
        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);

        if (exercise.getAssessmentDueDate() == null || ZonedDateTime.now().isAfter(exercise.getAssessmentDueDate())) {
            // to avoid canceling more important tasks we simply return here.
            // to make sure no wrong notification is sent out the date is checked again in the concrete notification method
            return;
        }
        if (exercise.isCourseExercise()) {
            scheduleNotificationForAssessedExercisesSubmissions(exercise, submission);
        }
    }

    /**
     * The place where the actual tasks/methods are called/scheduled that should be run when at the assessment due date of the exercise
     * @param exercise which will be announced by a notifications at release date
     */
    private void scheduleNotificationForAssessedExercisesSubmissions(Exercise exercise, Submission submission) {
        try {
            checkSecurityUtils();
            scheduleService.scheduleTask(exercise, ExerciseLifecycle.ASSESSMENT_DUE, () -> {
                checkSecurityUtils();
                // if the exercise has been updated in the meantime the scheduled immutable exercise is outdated and has to be replaced by the current one in the DB
                Exercise foundCurrentVersionOfScheduledExercise;
                try {
                    foundCurrentVersionOfScheduledExercise = exerciseRepository.findByIdElseThrow(exercise.getId());
                }
                catch (EntityNotFoundException entityNotFoundException) {
                    log.debug("Exercise is no longer in the database " + exercise.getId());
                    return;
                }

                // check if submission is still available (could have been deleted in the meantime)
                Submission foundCurrentVersionOfScheduledSubmission;
                try {
                    // findByIdWithResultsElseThrow() is strangely throwing an EntityNotFound exception even though the submission is found
                    foundCurrentVersionOfScheduledSubmission = submissionRepository.findWithEagerResultsAndAssessorById(submission.getId()).orElseThrow();
                }
                catch (EntityNotFoundException entityNotFoundException) {
                    log.debug("Submission is no longer in the database " + submission.getId());
                    return;
                }

                // check if user is available
                User student = ((StudentParticipation) foundCurrentVersionOfScheduledSubmission.getParticipation()).getStudent().orElseThrow();

                // only send a notification if AssessmentDueDate is defined and not in the future (i.e. in the range [now-2 minutes, now]) (due to possible delays in scheduling)
                ZonedDateTime assessmentDueDate = foundCurrentVersionOfScheduledExercise.getAssessmentDueDate();
                if (assessmentDueDate != null && !assessmentDueDate.isBefore(ZonedDateTime.now().minusMinutes(2)) && !assessmentDueDate.isAfter(ZonedDateTime.now())) {
                    singleUserNotificationService.notifyUserAboutAssessedExerciseSubmission(foundCurrentVersionOfScheduledExercise, student);
                }
            });
            log.debug("Scheduled notify about assessed exercise submission after assessment due date for exercise '{}' (#{}) and submission (#{}) at {}.", exercise.getTitle(),
                    exercise.getId(), exercise.getAssessmentDueDate(), submission.getId());
        }
        catch (Exception exception) {
            log.error("Failed to schedule notification for exercise " + exercise.getId(), exception);
        }
    }
}
