package de.tum.cit.aet.artemis.atlas.service;

import java.util.Collections;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.dto.CourseAutoOrchestrationConfigDTO;
import de.tum.cit.aet.artemis.atlas.repository.CourseAutoOrchestrationConfigurationRepository;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggleService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.event.ExerciseVersionCreatedEvent;
import de.tum.cit.aet.artemis.exercise.service.ExerciseVersionService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

/**
 * Feeds the automatic competency pipeline whenever an exercise version is created. Hooks into the
 * existing {@link ExerciseVersionCreatedEvent} publisher so every authoring path (programming,
 * text, quiz, modeling, file upload) triggers the debounce pipeline without bespoke wiring per
 * resource.
 * <p>
 * Everything is gated behind the {@link Feature#AtlasAgent} toggle, which is
 * disabled by default — instructors can opt in per-instance via the feature-toggle admin UI — and,
 * additionally, behind the per-course {@code autoOrchestratorEnabled} kill switch: a course only
 * participates in the pipeline when the instructor has explicitly enabled it. Exam exercises are
 * skipped because competency management is scoped to course content only.
 * <p>
 * The recording is further filtered by the changed-field set carried on the event
 * ({@link ExerciseVersionCreatedEvent#changedFields()}): only versions that touched a
 * {@link ExerciseVersionService#COMPETENCY_RELEVANT_FIELDS content-bearing field} record into the
 * accumulator, so purely administrative edits (dates, points, grading config, …) never burn the
 * per-course daily cap on an orchestration that could not change competency mapping.
 */
@Conditional(AtlasEnabled.class)
@Lazy
@Component
public class AutonomousCompetencyExerciseEventListener {

    private static final Logger log = LoggerFactory.getLogger(AutonomousCompetencyExerciseEventListener.class);

    private final ContentChangeAccumulatorService accumulator;

    private final FeatureToggleService featureToggleService;

    private final CourseAutoOrchestrationConfigurationRepository autoOrchestrationConfigurationRepository;

    public AutonomousCompetencyExerciseEventListener(ContentChangeAccumulatorService accumulator, FeatureToggleService featureToggleService,
            CourseAutoOrchestrationConfigurationRepository autoOrchestrationConfigurationRepository) {
        this.accumulator = accumulator;
        this.featureToggleService = featureToggleService;
        this.autoOrchestrationConfigurationRepository = autoOrchestrationConfigurationRepository;
    }

    /**
     * Fires on every {@link ExerciseVersionCreatedEvent} — publishers live in the exercise module,
     * so one listener covers every authoring path (programming / text / modeling / quiz / file
     * upload). The method is a no-op when the global toggle is off, when the exercise is an exam
     * exercise, when any null guard trips, or when the change touched no content-bearing field; in
     * the success path it merges the exercise id into the per-course accumulator for the scheduler to
     * pick up.
     * <p>
     * When the owning course has auto-orchestration disabled the method flushes the course's
     * accumulator bucket (dropping any ids buffered while it was enabled) and returns without
     * recording, so disabling acts as an immediate per-course kill switch.
     *
     * @param event the just-published event carrying the newly versioned exercise and its changed fields
     */
    @EventListener
    @Async
    public void onExerciseVersionCreated(ExerciseVersionCreatedEvent event) {
        SecurityUtils.setAuthorizationObject();
        if (!featureToggleService.isFeatureEnabled(Feature.AtlasAgent)) {
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
        long courseId = course.getId();
        boolean autoOrchestratorEnabled = autoOrchestrationConfigurationRepository.findConfigByCourseId(courseId).map(CourseAutoOrchestrationConfigDTO::autoOrchestratorEnabled)
                .orElse(false);
        if (!autoOrchestratorEnabled) {
            // Per-course kill switch is off: drop anything buffered while it was on so a later
            // re-enable or scheduler tick cannot resurrect stale changes for a disabled course.
            accumulator.flush(courseId);
            return;
        }
        // Filter on the changed-field set: only record when the version touched a content-bearing
        // field that could affect competency mapping. An empty set (e.g. legacy events) is treated
        // as not relevant.
        Set<String> changedFields = event.changedFields() == null ? Collections.emptySet() : event.changedFields();
        if (Collections.disjoint(changedFields, ExerciseVersionService.COMPETENCY_RELEVANT_FIELDS)) {
            log.debug("atlas.automatic skipping exercise change courseId={} exerciseId={}: no competency-relevant field changed (changed={})", courseId, exercise.getId(),
                    changedFields);
            return;
        }
        log.debug("atlas.automatic recorded exercise change courseId={} exerciseId={}", courseId, exercise.getId());
        accumulator.record(courseId, exercise.getId());
    }
}
