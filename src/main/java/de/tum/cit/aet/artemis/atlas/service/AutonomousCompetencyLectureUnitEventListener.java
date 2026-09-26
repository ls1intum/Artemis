package de.tum.cit.aet.artemis.atlas.service;

import java.util.Optional;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.atlas.config.AtlasLLMEnabled;
import de.tum.cit.aet.artemis.atlas.dto.CourseAutoOrchestrationConfigDTO;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggleService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.repository.CourseConfigurationRepository;
import de.tum.cit.aet.artemis.lecture.api.LectureUnitRepositoryApi;
import de.tum.cit.aet.artemis.lecture.domain.ExerciseUnit;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;
import de.tum.cit.aet.artemis.lecture.domain.event.LectureUnitContentChangedEvent;

/**
 * Feeds the automatic competency pipeline whenever a lecture unit's content changes. Clone of
 * {@link AutonomousCompetencyExerciseEventListener} for the lecture-unit side: it hooks into the
 * {@link LectureUnitContentChangedEvent} publisher so every content-bearing text/online/attachment-video
 * edit (create or update) triggers the debounce pipeline without bespoke wiring per resource.
 * <p>
 * Everything is gated behind the {@link Feature#AtlasAgent} toggle (disabled by default) and,
 * additionally, behind the per-course {@code autoOrchestratorEnabled} kill switch: a course only
 * participates when the instructor has explicitly enabled it. {@link ExerciseUnit}s are skipped because
 * they carry no content of their own — their exercise is orchestrated directly, and
 * {@code CourseCompetency.prePersistOrUpdate} strips any competency link to them.
 * <p>
 * Unlike the exercise listener there is no changed-field filter here: the publishing resource already
 * fires the event only when a content-bearing field actually changed, so every event that reaches this
 * listener is competency-relevant.
 */
@Conditional(AtlasLLMEnabled.class)
@Lazy
@Component
public class AutonomousCompetencyLectureUnitEventListener {

    private final Optional<LectureUnitRepositoryApi> lectureUnitRepositoryApi;

    private final ContentChangeAccumulatorService accumulator;

    private final FeatureToggleService featureToggleService;

    private final CourseConfigurationRepository courseConfigurationRepository;

    public AutonomousCompetencyLectureUnitEventListener(ContentChangeAccumulatorService accumulator, FeatureToggleService featureToggleService,
            CourseConfigurationRepository courseConfigurationRepository, Optional<LectureUnitRepositoryApi> lectureUnitRepositoryApi) {
        this.lectureUnitRepositoryApi = lectureUnitRepositoryApi;
        this.accumulator = accumulator;
        this.featureToggleService = featureToggleService;
        this.courseConfigurationRepository = courseConfigurationRepository;
    }

    /**
     * Fires on every {@link LectureUnitContentChangedEvent}. When both orchestration switches are
     * enabled, refreshes the queued id using the current persisted content: eligible units are
     * recorded and ineligible units are removed. Disabled courses flush their entire bucket so a
     * later re-enable cannot resurrect stale changes. Null or unpersisted event units are ignored.
     *
     * @param event the just-published event carrying the changed lecture unit
     */
    @EventListener
    @Async
    public void onLectureUnitContentChanged(LectureUnitContentChangedEvent event) {
        SecurityUtils.setAuthorizationObject();
        if (!featureToggleService.isFeatureEnabled(Feature.AtlasAgent)) {
            return;
        }
        LectureUnit lectureUnit = event.lectureUnit();
        if (lectureUnit == null || lectureUnit.getId() == null || lectureUnit instanceof ExerciseUnit) {
            return;
        }
        Lecture lecture = lectureUnit.getLecture();
        if (lecture == null) {
            return;
        }
        Course course = lecture.getCourse();
        if (course == null || course.getId() == null) {
            return;
        }
        long courseId = course.getId();
        boolean autoOrchestratorEnabled = courseConfigurationRepository.findAutoOrchestrationConfigByCourseId(courseId)
                .map(CourseAutoOrchestrationConfigDTO::autoOrchestratorEnabled).orElse(false);
        if (!autoOrchestratorEnabled) {
            // Per-course kill switch is off: drop anything buffered while it was on so a later re-enable
            // or scheduler tick cannot resurrect stale changes for a disabled course.
            accumulator.flush(courseId);
            return;
        }
        accumulator.refreshLectureUnit(courseId, lectureUnit.getId(),
                () -> lectureUnitRepositoryApi.flatMap(api -> api.findWithLectureById(lectureUnit.getId()))
                        .filter(current -> current.getLecture() != null && current.getLecture().getCourse() != null
                                && Long.valueOf(courseId).equals(current.getLecture().getCourse().getId()))
                        .map(ContentExtractionService::isLectureUnitEligibleForOrchestration).orElse(false));
    }
}
