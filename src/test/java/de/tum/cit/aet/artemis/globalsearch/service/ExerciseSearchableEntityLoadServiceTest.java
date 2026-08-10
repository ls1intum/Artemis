package de.tum.cit.aet.artemis.globalsearch.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.globalsearch.dto.searchableentity.ExerciseSearchableEntityDTO;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

/**
 * Unit tests for {@link ExerciseSearchableEntityLoadService}, focused on the differing error semantics of its two
 * loading paths. The migration-oriented batch {@link ExerciseSearchableEntityLoadService#loadExerciseDtos} skips an
 * exercise it cannot map so one bad row cannot abort a backfill. The strict single-entity
 * {@link ExerciseSearchableEntityLoadService#loadExerciseDtoForResolve}, used by the outbox dispatcher, must instead
 * return empty only when the row is absent and let loading or mapping failures propagate, so a failure is retried
 * rather than mistaken for a deletion of a valid index row.
 */
class ExerciseSearchableEntityLoadServiceTest {

    private final ExerciseRepository exerciseRepository = mock(ExerciseRepository.class);

    private ExerciseSearchableEntityLoadService service;

    @BeforeEach
    void setUp() {
        service = new ExerciseSearchableEntityLoadService(exerciseRepository);
    }

    @Test
    void loadExerciseDtoForResolve_returnsEmptyWhenExerciseAbsent() {
        when(exerciseRepository.findAllForSearchMigrationWithCourseAndExam(List.of(42L))).thenReturn(List.of());

        assertThat(service.loadExerciseDtoForResolve(42L)).isEmpty();
    }

    @Test
    void loadExerciseDtoForResolve_returnsDtoWhenExercisePresent() {
        when(exerciseRepository.findAllForSearchMigrationWithCourseAndExam(List.of(5L))).thenReturn(List.of(mappableExercise(5L, 100L)));

        Optional<ExerciseSearchableEntityDTO> result = service.loadExerciseDtoForResolve(5L);

        assertThat(result).isPresent();
        assertThat(result.get().exerciseId()).isEqualTo(5L);
        assertThat(result.get().courseId()).isEqualTo(100L);
    }

    @Test
    void loadExerciseDtoForResolve_propagatesMappingFailureInsteadOfReturningEmpty() {
        // A present-but-unmappable exercise must not be reported as absent: the dispatcher would then delete the valid
        // Weaviate row and drop the outbox entry instead of retrying the failure.
        when(exerciseRepository.findAllForSearchMigrationWithCourseAndExam(List.of(7L))).thenReturn(List.of(unmappableExercise(7L)));

        assertThatThrownBy(() -> service.loadExerciseDtoForResolve(7L)).isInstanceOf(RuntimeException.class);
    }

    @Test
    void loadExerciseDtoForResolve_propagatesLoadingFailure() {
        when(exerciseRepository.findAllForSearchMigrationWithCourseAndExam(List.of(9L))).thenThrow(new RuntimeException("database unavailable"));

        assertThatThrownBy(() -> service.loadExerciseDtoForResolve(9L)).isInstanceOf(RuntimeException.class).hasMessageContaining("database unavailable");
    }

    @Test
    void loadExerciseDtos_skipsUnmappableExerciseForMigration() {
        // Contrast with the strict path: the batch migration deliberately swallows a single unmappable exercise.
        when(exerciseRepository.findAllForSearchMigrationWithCourseAndExam(List.of(7L))).thenReturn(List.of(unmappableExercise(7L)));

        assertThat(service.loadExerciseDtos(List.of(7L))).isEmpty();
    }

    private static Exercise mappableExercise(long exerciseId, long courseId) {
        Course course = new Course();
        course.setId(courseId);
        TextExercise exercise = new TextExercise();
        exercise.setId(exerciseId);
        exercise.setTitle("Essay");
        exercise.setCourse(course);
        return exercise;
    }

    private static Exercise unmappableExercise(long exerciseId) {
        // No course set, so fromExercise dereferences a null course and throws.
        TextExercise exercise = new TextExercise();
        exercise.setId(exerciseId);
        return exercise;
    }
}
