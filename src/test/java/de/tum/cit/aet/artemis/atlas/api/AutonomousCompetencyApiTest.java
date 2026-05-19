package de.tum.cit.aet.artemis.atlas.api;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tum.cit.aet.artemis.atlas.service.ContentChangeAccumulatorService;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggleService;

/**
 * Exercises the manual notification path for lecture units. Lecture units have no equivalent to
 * {@code ExerciseVersionCreatedEvent}, so each lecture-unit creator (the relevant service or
 * resource) calls {@link AutonomousCompetencyApi#notifyLectureUnitChange}; the tests here lock in
 * the feature-toggle gate and the accumulator hand-off.
 */
@ExtendWith(MockitoExtension.class)
class AutonomousCompetencyApiTest {

    private static final long COURSE_ID = 15L;

    private static final long LECTURE_UNIT_ID = 55L;

    @Mock
    private ContentChangeAccumulatorService accumulator;

    @Mock
    private FeatureToggleService featureToggleService;

    private AutonomousCompetencyApi api;

    @BeforeEach
    void setUp() {
        api = new AutonomousCompetencyApi(accumulator, featureToggleService);
    }

    @Test
    void notifyLectureUnitChange_recordsAccumulator() {
        when(featureToggleService.isFeatureEnabled(Feature.AutomaticCompetencyManagement)).thenReturn(true);

        api.notifyLectureUnitChange(COURSE_ID, LECTURE_UNIT_ID);

        verify(accumulator).record(COURSE_ID, LECTURE_UNIT_ID, true);
    }

    @Test
    void notifyLectureUnitChange_toggleOff_doesNothing() {
        when(featureToggleService.isFeatureEnabled(Feature.AutomaticCompetencyManagement)).thenReturn(false);

        api.notifyLectureUnitChange(COURSE_ID, LECTURE_UNIT_ID);

        verify(accumulator, never()).record(anyLong(), anyLong(), anyBoolean());
    }
}
