package de.tum.cit.aet.artemis.atlas.service;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tum.cit.aet.artemis.atlas.dto.CourseAutoOrchestrationConfigDTO;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggleService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.repository.CourseConfigurationRepository;
import de.tum.cit.aet.artemis.lecture.domain.ExerciseUnit;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.TextUnit;
import de.tum.cit.aet.artemis.lecture.domain.event.LectureUnitContentChangedEvent;

/**
 * Behaviour of {@link AutonomousCompetencyLectureUnitEventListener} — the event listener that feeds
 * the automatic pipeline from lecture-unit content-changed events. Verifies the feature-toggle gate,
 * the per-course kill switch (with flush-on-disable), the ExerciseUnit skip, and the null guards
 * without needing a full Spring context (mirrors {@link AutonomousCompetencyExerciseEventListenerTest}).
 */
@ExtendWith(MockitoExtension.class)
class AutonomousCompetencyLectureUnitEventListenerTest {

    private static final long COURSE_ID = 77L;

    private static final long LECTURE_UNIT_ID = 30L;

    @Mock
    private ContentChangeAccumulatorService accumulator;

    @Mock
    private FeatureToggleService featureToggleService;

    @Mock
    private CourseConfigurationRepository courseConfigurationRepository;

    private AutonomousCompetencyLectureUnitEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new AutonomousCompetencyLectureUnitEventListener(accumulator, featureToggleService, courseConfigurationRepository);
    }

    private void stubCourseEnabled(boolean enabled) {
        when(courseConfigurationRepository.findAutoOrchestrationConfigByCourseId(COURSE_ID)).thenReturn(Optional.of(new CourseAutoOrchestrationConfigDTO(enabled, null, null)));
    }

    @Test
    void onLectureUnitContentChanged_toggleEnabledCourseEnabled_recordsAccumulator() {
        when(featureToggleService.isFeatureEnabled(Feature.AtlasAgent)).thenReturn(true);
        stubCourseEnabled(true);
        TextUnit unit = courseLectureUnit();

        listener.onLectureUnitContentChanged(new LectureUnitContentChangedEvent(unit));

        verify(accumulator).recordLectureUnit(COURSE_ID, LECTURE_UNIT_ID);
    }

    @Test
    void onLectureUnitContentChanged_toggleDisabled_doesNothing() {
        when(featureToggleService.isFeatureEnabled(Feature.AtlasAgent)).thenReturn(false);

        listener.onLectureUnitContentChanged(new LectureUnitContentChangedEvent(courseLectureUnit()));

        verify(accumulator, never()).recordLectureUnit(anyLong(), anyLong());
        verify(accumulator, never()).flush(anyLong());
    }

    @Test
    void onLectureUnitContentChanged_courseDisabled_flushesAndDoesNotRecord() {
        when(featureToggleService.isFeatureEnabled(Feature.AtlasAgent)).thenReturn(true);
        stubCourseEnabled(false);

        listener.onLectureUnitContentChanged(new LectureUnitContentChangedEvent(courseLectureUnit()));

        verify(accumulator).flush(COURSE_ID);
        verify(accumulator, never()).recordLectureUnit(anyLong(), anyLong());
    }

    @Test
    void onLectureUnitContentChanged_exerciseUnit_skipped() {
        when(featureToggleService.isFeatureEnabled(Feature.AtlasAgent)).thenReturn(true);
        ExerciseUnit exerciseUnit = new ExerciseUnit();
        exerciseUnit.setLecture(lectureInCourse());

        listener.onLectureUnitContentChanged(new LectureUnitContentChangedEvent(exerciseUnit));

        verify(accumulator, never()).recordLectureUnit(anyLong(), anyLong());
        verify(accumulator, never()).flush(anyLong());
    }

    @Test
    void onLectureUnitContentChanged_nullLectureUnit_safe() {
        when(featureToggleService.isFeatureEnabled(Feature.AtlasAgent)).thenReturn(true);

        listener.onLectureUnitContentChanged(new LectureUnitContentChangedEvent(null));

        verify(accumulator, never()).recordLectureUnit(anyLong(), anyLong());
    }

    @Test
    void onLectureUnitContentChanged_noLecture_safe() {
        when(featureToggleService.isFeatureEnabled(Feature.AtlasAgent)).thenReturn(true);
        TextUnit unit = new TextUnit();
        unit.setId(LECTURE_UNIT_ID);
        // No lecture set: the null guard must short-circuit.

        listener.onLectureUnitContentChanged(new LectureUnitContentChangedEvent(unit));

        verify(accumulator, never()).recordLectureUnit(anyLong(), anyLong());
    }

    private TextUnit courseLectureUnit() {
        TextUnit unit = new TextUnit();
        unit.setId(LECTURE_UNIT_ID);
        unit.setName("Recursion basics");
        unit.setLecture(lectureInCourse());
        return unit;
    }

    private static Lecture lectureInCourse() {
        Course course = new Course();
        course.setId(COURSE_ID);
        Lecture lecture = new Lecture();
        lecture.setCourse(course);
        return lecture;
    }
}
