package de.tum.cit.aet.artemis.course.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.service.messaging.InstanceMessageSendService;
import de.tum.cit.aet.artemis.course.dto.CourseAthenaConfigDTO;
import de.tum.cit.aet.artemis.course.dto.CourseAthenaConfigUpdateDTO;
import de.tum.cit.aet.artemis.course.repository.CourseAthenaConfigRepository;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

/**
 * Reads and writes the course-level Athena configuration and keeps Athena's due-date scheduling in sync with it.
 */
@Profile(PROFILE_CORE)
@Service
@Lazy
public class CourseAthenaConfigService {

    private final CourseAthenaConfigRepository courseAthenaConfigRepository;

    private final ExerciseRepository exerciseRepository;

    private final InstanceMessageSendService instanceMessageSendService;

    public CourseAthenaConfigService(CourseAthenaConfigRepository courseAthenaConfigRepository, ExerciseRepository exerciseRepository,
            InstanceMessageSendService instanceMessageSendService) {
        this.courseAthenaConfigRepository = courseAthenaConfigRepository;
        this.exerciseRepository = exerciseRepository;
        this.instanceMessageSendService = instanceMessageSendService;
    }

    /**
     * Returns the Athena configuration of the given course.
     *
     * @param courseId the id of the course to read the configuration of
     * @return the course's Athena configuration, all flags disabled when the course has no configuration yet
     */
    public CourseAthenaConfigDTO getConfig(long courseId) {
        return courseAthenaConfigRepository.findConfigByCourseId(courseId).orElseThrow(() -> new EntityNotFoundException("Course", courseId));
    }

    /**
     * Applies the requested changes to the course's Athena configuration, creating the configuration if the course does
     * not have one yet, and republishes Athena scheduling when the grading feedback flag actually changed.
     * <p>
     * Each requested flag is written by its own conditional statement, so a request changes only the feature it names
     * and cannot carry a stale value for the other one back into the database. Whether the grading flag changed is
     * decided by that statement rather than by a value read beforehand, so a concurrent change cannot make this skip
     * the scheduling refresh.
     *
     * @param courseId the id of the course to configure
     * @param update   the flags to change; a null flag is left as it is
     * @return the stored configuration
     */
    public CourseAthenaConfigDTO updateConfig(long courseId, CourseAthenaConfigUpdateDTO update) {
        long configId = courseAthenaConfigRepository.ensureAthenaConfigExists(courseId);

        boolean gradingFeedbackChanged = update.gradingFeedbackEnabled() != null
                && courseAthenaConfigRepository.updateGradingFeedbackEnabled(configId, update.gradingFeedbackEnabled()) > 0;
        if (update.formativeFeedbackEnabled() != null) {
            courseAthenaConfigRepository.updateFormativeFeedbackEnabled(configId, update.formativeFeedbackEnabled());
        }

        if (gradingFeedbackChanged) {
            refreshAthenaSchedulingForCourseExercises(courseId);
        }

        return courseAthenaConfigRepository.getArbitraryValueElseThrow(courseAthenaConfigRepository.findConfigById(configId), String.valueOf(configId));
    }

    /**
     * Publishes a scheduling refresh for every exercise of the course whose type is wired for Athena due-date scheduling
     * (see {@code AthenaScheduleService}), so the scheduling node creates or cancels each Athena task based on the
     * course's current grading feedback configuration. Without this, enabling the flag would leave already-existing
     * exercises unscheduled until the next server restart, and disabling it would leave already-scheduled tasks running.
     *
     * @param courseId the id of the course whose Athena grading feedback flag was just changed
     */
    private void refreshAthenaSchedulingForCourseExercises(long courseId) {
        for (Exercise exercise : exerciseRepository.findAllAthenaSchedulableExercisesWithFutureDueDateByCourseId(courseId)) {
            switch (exercise) {
                case ProgrammingExercise programmingExercise -> instanceMessageSendService.sendProgrammingExerciseSchedule(programmingExercise.getId());
                case TextExercise textExercise -> instanceMessageSendService.sendTextExerciseSchedule(textExercise.getId());
                default -> {
                    // no other exercise type is currently wired for Athena due-date scheduling
                }
            }
        }
    }
}
