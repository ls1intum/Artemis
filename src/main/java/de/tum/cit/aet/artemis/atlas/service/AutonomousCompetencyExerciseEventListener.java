package de.tum.cit.aet.artemis.atlas.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggleService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.event.ExerciseVersionCreatedEvent;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

/**
 * Feeds the automatic competency pipeline whenever an exercise version is created. Hooks into the
 * existing {@link ExerciseVersionCreatedEvent} publisher so every authoring path (programming,
 * text, quiz, modeling, file upload) triggers the debounce pipeline without bespoke wiring per
 * resource.
 * <p>
 * Everything is gated behind the {@link Feature#AutomaticCompetencyManagement} toggle, which is
 * disabled by default — instructors can opt in per-instance via the feature-toggle admin UI. Exam
 * exercises are skipped because competency management is scoped to course content only.
 */
@Conditional(AtlasEnabled.class)
@Lazy
@Component
public class AutonomousCompetencyExerciseEventListener {

    private static final Logger log = LoggerFactory.getLogger(AutonomousCompetencyExerciseEventListener.class);

    private final ContentChangeAccumulatorService accumulator;

    private final FeatureToggleService featureToggleService;

    public AutonomousCompetencyExerciseEventListener(ContentChangeAccumulatorService accumulator, FeatureToggleService featureToggleService) {
        this.accumulator = accumulator;
        this.featureToggleService = featureToggleService;
    }

    /**
     * Fires on every {@link ExerciseVersionCreatedEvent} — publishers live in the exercise module,
     * so one listener covers every authoring path (programming / text / modeling / quiz / file
     * upload). The method is a no-op when the toggle is off, when the exercise is an exam exercise,
     * or when any null guard trips; in the success path it merges the exercise id into the
     * per-course accumulator for the scheduler to pick up.
     *
     * @param event the just-published event carrying the newly versioned exercise
     */
    @EventListener
    @Async
    public void onExerciseVersionCreated(ExerciseVersionCreatedEvent event) {
        SecurityUtils.setAuthorizationObject();
        if (!featureToggleService.isFeatureEnabled(Feature.AutomaticCompetencyManagement)) {
            return;
        }
        Exercise exercise = event.exercise();
        if (exercise == null || exercise.getId() == null || exercise.isExamExercise()) {
            return;
        }
        // Orchestrator currently only supports programming exercises — recording other types would
        // burn the per-course daily cap on guaranteed-failure runs.
        if (!(exercise instanceof ProgrammingExercise)) {
            return;
        }
        Course course = exercise.getCourseViaExerciseGroupOrCourseMember();
        if (course == null || course.getId() == null) {
            return;
        }
        log.debug("atlas.automatic recorded exercise change courseId={} exerciseId={}", course.getId(), exercise.getId());
        accumulator.record(course.getId(), exercise.getId(), false);
    }
}
