package de.tum.cit.aet.artemis.atlas.service;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggleService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exercise.domain.event.ExerciseVersionCreatedEvent;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

/**
 * Behaviour of {@link AutonomousCompetencyExerciseEventListener} — the event listener that feeds
 * the automatic pipeline from exercise creation / update events. Verifies the feature-toggle gate,
 * the programming-exercise scope filter, exam filtering, and null guards without needing a full
 * Spring context.
 */
@ExtendWith(MockitoExtension.class)
class AutonomousCompetencyExerciseEventListenerTest {

    private static final long COURSE_ID = 77L;

    private static final long EXERCISE_ID = 9L;

    @Mock
    private ContentChangeAccumulatorService accumulator;

    @Mock
    private FeatureToggleService featureToggleService;

    private AutonomousCompetencyExerciseEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new AutonomousCompetencyExerciseEventListener(accumulator, featureToggleService);
    }

    @Test
    void onExerciseVersionCreated_toggleEnabled_recordsAccumulator() {
        when(featureToggleService.isFeatureEnabled(Feature.AutomaticCompetencyManagement)).thenReturn(true);
        ProgrammingExercise exercise = courseExercise();

        listener.onExerciseVersionCreated(new ExerciseVersionCreatedEvent(exercise));

        verify(accumulator).record(COURSE_ID, EXERCISE_ID, false);
    }

    @Test
    void onExerciseVersionCreated_toggleDisabled_doesNothing() {
        when(featureToggleService.isFeatureEnabled(Feature.AutomaticCompetencyManagement)).thenReturn(false);
        ProgrammingExercise exercise = courseExercise();

        listener.onExerciseVersionCreated(new ExerciseVersionCreatedEvent(exercise));

        verify(accumulator, never()).record(anyLong(), anyLong(), anyBoolean());
    }

    @Test
    void onExerciseVersionCreated_examExercise_filtered() {
        when(featureToggleService.isFeatureEnabled(Feature.AutomaticCompetencyManagement)).thenReturn(true);
        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setId(EXERCISE_ID);
        ExerciseGroup group = new ExerciseGroup();
        exercise.setExerciseGroup(group);

        listener.onExerciseVersionCreated(new ExerciseVersionCreatedEvent(exercise));

        verify(accumulator, never()).record(anyLong(), anyLong(), anyBoolean());
    }

    @Test
    void onExerciseVersionCreated_nonProgrammingExercise_filtered() {
        when(featureToggleService.isFeatureEnabled(Feature.AutomaticCompetencyManagement)).thenReturn(true);
        Course course = new Course();
        course.setId(COURSE_ID);
        TextExercise exercise = new TextExercise();
        exercise.setId(EXERCISE_ID);
        exercise.setCourse(course);

        listener.onExerciseVersionCreated(new ExerciseVersionCreatedEvent(exercise));

        verify(accumulator, never()).record(anyLong(), anyLong(), anyBoolean());
    }

    @Test
    void onExerciseVersionCreated_nullExercise_safe() {
        when(featureToggleService.isFeatureEnabled(Feature.AutomaticCompetencyManagement)).thenReturn(true);

        listener.onExerciseVersionCreated(new ExerciseVersionCreatedEvent(null));

        verify(accumulator, never()).record(anyLong(), anyLong(), anyBoolean());
    }

    @Test
    void onExerciseVersionCreated_dedupedByAccumulator() {
        when(featureToggleService.isFeatureEnabled(Feature.AutomaticCompetencyManagement)).thenReturn(true);
        ProgrammingExercise exercise = courseExercise();

        listener.onExerciseVersionCreated(new ExerciseVersionCreatedEvent(exercise));
        listener.onExerciseVersionCreated(new ExerciseVersionCreatedEvent(exercise));

        verify(accumulator, times(2)).record(COURSE_ID, EXERCISE_ID, false);
    }

    private ProgrammingExercise courseExercise() {
        Course course = new Course();
        course.setId(COURSE_ID);
        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setId(EXERCISE_ID);
        exercise.setCourse(course);
        return exercise;
    }
}
