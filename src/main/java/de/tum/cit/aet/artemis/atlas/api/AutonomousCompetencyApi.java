package de.tum.cit.aet.artemis.atlas.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.service.ContentChangeAccumulatorService;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggleService;
import de.tum.cit.aet.artemis.lecture.domain.ExerciseUnit;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitRepository;

/**
 * Bridge between the lecture module and the automatic competency pipeline. Exercises publish
 * {@link de.tum.cit.aet.artemis.exercise.domain.event.ExerciseVersionCreatedEvent} which the
 * {@link de.tum.cit.aet.artemis.atlas.service.AutonomousCompetencyExerciseEventListener} picks up
 * directly — lecture units do not have an equivalent event, so each lecture-unit resource injects
 * this API (as {@code Optional<AutonomousCompetencyApi>}) and calls
 * {@link #notifyLectureUnitChange(long, long)} after a successful POST or PUT.
 * <p>
 * Wrapping the call here keeps the accumulator service out of the lecture module's classpath and
 * centralises gating logic (feature toggle, {@link ExerciseUnit} filter, missing-unit handling).
 */
@Conditional(AtlasEnabled.class)
@Lazy
@Controller
public class AutonomousCompetencyApi extends AbstractAtlasApi {

    private static final Logger log = LoggerFactory.getLogger(AutonomousCompetencyApi.class);

    private final ContentChangeAccumulatorService accumulator;

    private final FeatureToggleService featureToggleService;

    private final LectureUnitRepository lectureUnitRepository;

    public AutonomousCompetencyApi(ContentChangeAccumulatorService accumulator, FeatureToggleService featureToggleService, LectureUnitRepository lectureUnitRepository) {
        this.accumulator = accumulator;
        this.featureToggleService = featureToggleService;
        this.lectureUnitRepository = lectureUnitRepository;
    }

    /**
     * Record a lecture-unit change for the automatic pipeline. Silently no-ops when:
     * <ul>
     * <li>the feature toggle is disabled,</li>
     * <li>the lecture unit cannot be found (concurrent delete race), or</li>
     * <li>the unit is an {@link ExerciseUnit} — its underlying exercise is already covered by
     * the exercise event listener, and recording it a second time would double-count.</li>
     * </ul>
     *
     * @param courseId      the course the lecture unit belongs to; trusted from the caller so we do
     *                          not re-resolve it through the lecture graph
     * @param lectureUnitId id of the lecture unit that was just created or updated
     */
    public void notifyLectureUnitChange(long courseId, long lectureUnitId) {
        if (!featureToggleService.isFeatureEnabled(Feature.AutomaticCompetencyManagement)) {
            return;
        }
        LectureUnit lectureUnit = lectureUnitRepository.findById(lectureUnitId).orElse(null);
        if (lectureUnit == null) {
            log.debug("atlas.automatic lecture unit {} not found when notifying change — ignoring", lectureUnitId);
            return;
        }
        if (lectureUnit instanceof ExerciseUnit) {
            return;
        }
        log.debug("atlas.automatic recorded lecture-unit change courseId={} lectureUnitId={}", courseId, lectureUnitId);
        accumulator.record(courseId, lectureUnitId, true);
    }
}
