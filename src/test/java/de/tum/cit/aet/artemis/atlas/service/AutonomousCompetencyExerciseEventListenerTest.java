package de.tum.cit.aet.artemis.atlas.service;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tum.cit.aet.artemis.atlas.dto.CourseAutoOrchestrationConfigDTO;
import de.tum.cit.aet.artemis.atlas.repository.CourseAutoOrchestrationConfigurationRepository;
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
 * the per-course kill switch (with flush-on-disable), the content-relevant changed-field filter,
 * the programming-exercise scope filter, exam filtering, and null guards without needing a full
 * Spring context.
 */
@ExtendWith(MockitoExtension.class)
class AutonomousCompetencyExerciseEventListenerTest {

    private static final long COURSE_ID = 77L;

    private static final long EXERCISE_ID = 9L;

    /** A change set that intersects the competency allowlist (problemStatement is content-bearing). */
    private static final Set<String> RELEVANT_FIELDS = Set.of("problemStatement");

    /** A change set that does not intersect the allowlist (dates / points are administrative). */
    private static final Set<String> IRRELEVANT_FIELDS = Set.of("dueDate", "maxPoints");

    @Mock
    private ContentChangeAccumulatorService accumulator;

    @Mock
    private FeatureToggleService featureToggleService;

    @Mock
    private CourseAutoOrchestrationConfigurationRepository autoOrchestrationConfigurationRepository;

    private AutonomousCompetencyExerciseEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new AutonomousCompetencyExerciseEventListener(accumulator, featureToggleService, autoOrchestrationConfigurationRepository);
    }

    private void stubCourseEnabled(boolean enabled) {
        when(autoOrchestrationConfigurationRepository.findConfigByCourseId(COURSE_ID)).thenReturn(Optional.of(new CourseAutoOrchestrationConfigDTO(enabled, null, null)));
    }

    @Test
    void onExerciseVersionCreated_toggleEnabledCourseEnabledRelevantChange_recordsAccumulator() {
        when(featureToggleService.isFeatureEnabled(Feature.AtlasAgent)).thenReturn(true);
        stubCourseEnabled(true);
        ProgrammingExercise exercise = courseExercise();

        listener.onExerciseVersionCreated(new ExerciseVersionCreatedEvent(exercise, RELEVANT_FIELDS));

        verify(accumulator).record(COURSE_ID, EXERCISE_ID);
    }

    @Test
    void onExerciseVersionCreated_toggleDisabled_doesNothing() {
        when(featureToggleService.isFeatureEnabled(Feature.AtlasAgent)).thenReturn(false);
        ProgrammingExercise exercise = courseExercise();

        listener.onExerciseVersionCreated(new ExerciseVersionCreatedEvent(exercise, RELEVANT_FIELDS));

        verify(accumulator, never()).record(anyLong(), anyLong());
        verify(accumulator, never()).flush(anyLong());
    }

    @Test
    void onExerciseVersionCreated_courseDisabled_flushesAndDoesNotRecord() {
        when(featureToggleService.isFeatureEnabled(Feature.AtlasAgent)).thenReturn(true);
        stubCourseEnabled(false);
        ProgrammingExercise exercise = courseExercise();

        listener.onExerciseVersionCreated(new ExerciseVersionCreatedEvent(exercise, RELEVANT_FIELDS));

        verify(accumulator).flush(COURSE_ID);
        verify(accumulator, never()).record(anyLong(), anyLong());
    }

    @Test
    void onExerciseVersionCreated_irrelevantChangeOnly_doesNotRecord() {
        when(featureToggleService.isFeatureEnabled(Feature.AtlasAgent)).thenReturn(true);
        stubCourseEnabled(true);
        ProgrammingExercise exercise = courseExercise();

        listener.onExerciseVersionCreated(new ExerciseVersionCreatedEvent(exercise, IRRELEVANT_FIELDS));

        verify(accumulator, never()).record(anyLong(), anyLong());
        verify(accumulator, never()).flush(anyLong());
    }

    @Test
    void onExerciseVersionCreated_mixedChange_records() {
        when(featureToggleService.isFeatureEnabled(Feature.AtlasAgent)).thenReturn(true);
        stubCourseEnabled(true);
        ProgrammingExercise exercise = courseExercise();
        // A version that touched both an administrative field and a content-bearing field must record.
        Set<String> mixedFields = Set.of("dueDate", "title");

        listener.onExerciseVersionCreated(new ExerciseVersionCreatedEvent(exercise, mixedFields));

        verify(accumulator).record(COURSE_ID, EXERCISE_ID);
    }

    @Test
    void onExerciseVersionCreated_emptyChangeSet_doesNotRecord() {
        when(featureToggleService.isFeatureEnabled(Feature.AtlasAgent)).thenReturn(true);
        stubCourseEnabled(true);
        ProgrammingExercise exercise = courseExercise();

        listener.onExerciseVersionCreated(new ExerciseVersionCreatedEvent(exercise, Set.of()));

        verify(accumulator, never()).record(anyLong(), anyLong());
    }

    @Test
    void onExerciseVersionCreated_examExercise_filtered() {
        when(featureToggleService.isFeatureEnabled(Feature.AtlasAgent)).thenReturn(true);
        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setId(EXERCISE_ID);
        ExerciseGroup group = new ExerciseGroup();
        exercise.setExerciseGroup(group);

        listener.onExerciseVersionCreated(new ExerciseVersionCreatedEvent(exercise, RELEVANT_FIELDS));

        verify(accumulator, never()).record(anyLong(), anyLong());
    }

    @Test
    void onExerciseVersionCreated_nonProgrammingExercise_filtered() {
        when(featureToggleService.isFeatureEnabled(Feature.AtlasAgent)).thenReturn(true);
        Course course = new Course();
        course.setId(COURSE_ID);
        TextExercise exercise = new TextExercise();
        exercise.setId(EXERCISE_ID);
        exercise.setCourse(course);

        listener.onExerciseVersionCreated(new ExerciseVersionCreatedEvent(exercise, RELEVANT_FIELDS));

        verify(accumulator, never()).record(anyLong(), anyLong());
    }

    @Test
    void onExerciseVersionCreated_nullExercise_safe() {
        when(featureToggleService.isFeatureEnabled(Feature.AtlasAgent)).thenReturn(true);

        listener.onExerciseVersionCreated(new ExerciseVersionCreatedEvent(null, RELEVANT_FIELDS));

        verify(accumulator, never()).record(anyLong(), anyLong());
    }

    @Test
    void onExerciseVersionCreated_dedupedByAccumulator() {
        when(featureToggleService.isFeatureEnabled(Feature.AtlasAgent)).thenReturn(true);
        stubCourseEnabled(true);
        ProgrammingExercise exercise = courseExercise();

        listener.onExerciseVersionCreated(new ExerciseVersionCreatedEvent(exercise, RELEVANT_FIELDS));
        listener.onExerciseVersionCreated(new ExerciseVersionCreatedEvent(exercise, RELEVANT_FIELDS));

        verify(accumulator, times(2)).record(COURSE_ID, EXERCISE_ID);
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
