package de.tum.cit.aet.artemis.atlas.api;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.service.ContentChangeAccumulatorService;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggleService;

/**
 * Bridge between the lecture module and the automatic competency pipeline. Exercises publish
 * {@link de.tum.cit.aet.artemis.exercise.domain.event.ExerciseVersionCreatedEvent} which the
 * {@link de.tum.cit.aet.artemis.atlas.service.AutonomousCompetencyExerciseEventListener} picks up
 * directly — lecture units do not have an equivalent event, so each lecture-unit creator injects
 * this API (as {@code Optional<AutonomousCompetencyApi>}) and calls
 * {@link #notifyLectureUnitChange(long, long)} after a successful save. Callers must only invoke
 * this for non-{@code ExerciseUnit} lecture units; the exercise pipeline already covers exercise
 * units via the event listener.
 */
@Conditional(AtlasEnabled.class)
@Lazy
@Controller
public class AutonomousCompetencyApi extends AbstractAtlasApi {

    private final ContentChangeAccumulatorService accumulator;

    private final FeatureToggleService featureToggleService;

    public AutonomousCompetencyApi(ContentChangeAccumulatorService accumulator, FeatureToggleService featureToggleService) {
        this.accumulator = accumulator;
        this.featureToggleService = featureToggleService;
    }

    /**
     * Record a lecture-unit change for the automatic pipeline. Silently no-ops when the feature
     * toggle is disabled. Callers must only invoke this for non-{@code ExerciseUnit} units —
     * exercise units are already covered by {@link
     * de.tum.cit.aet.artemis.atlas.service.AutonomousCompetencyExerciseEventListener}.
     *
     * @param courseId      the course the lecture unit belongs to; trusted from the caller so we do
     *                          not re-resolve it through the lecture graph
     * @param lectureUnitId id of the lecture unit that was just created or updated
     */
    public void notifyLectureUnitChange(long courseId, long lectureUnitId) {
        if (!featureToggleService.isFeatureEnabled(Feature.AtlasAgent)) {
            return;
        }
        accumulator.record(courseId, lectureUnitId, true);
    }
}
