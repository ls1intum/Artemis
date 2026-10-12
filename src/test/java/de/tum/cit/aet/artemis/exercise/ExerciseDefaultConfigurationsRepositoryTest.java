package de.tum.cit.aet.artemis.exercise;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseDefaultConfigurationsRepository;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

/**
 * Pins the default method that gives an exercise the permanent configuration rows of optional modules, in the cases that need a
 * racing or failing database to reach: the concurrent insert of the same row and a violation that is not one. The statements are
 * stubbed and the default methods run for real. The behavior against a real database, including the column defaults the insert
 * relies on, is covered by {@code MetisDataModelIntegrationTest}.
 */
class ExerciseDefaultConfigurationsRepositoryTest {

    private static final long EXERCISE_ID = 17L;

    private ExerciseDefaultConfigurationsRepository repository;

    @BeforeEach
    void setup() {
        repository = mock(ExerciseDefaultConfigurationsRepository.class, CALLS_REAL_METHODS);
    }

    private static <E extends Exercise> E withId(E exercise) {
        exercise.setId(EXERCISE_ID);
        return exercise;
    }

    @Test
    void anExerciseOfAnotherTypeOwnsNoMetisConfigurationAndReadsNothing() {
        for (Exercise exercise : List.of(withId(new TextExercise()), withId(new ModelingExercise()), withId(new FileUploadExercise()), withId(new QuizExercise()))) {
            repository.ensureDefaultConfigurations(exercise);
        }

        verify(repository, never()).countMetisExerciseConfigs(anyLong());
        verify(repository, never()).initializeMetisExerciseConfig(anyLong());
    }

    @Test
    void aProgrammingExerciseThatHasItsConfigurationInsertsNothing() {
        doReturn(1L).when(repository).countMetisExerciseConfigs(EXERCISE_ID);

        repository.ensureDefaultConfigurations(withId(new ProgrammingExercise()));

        verify(repository, never()).initializeMetisExerciseConfig(anyLong());
    }

    @Test
    void aProgrammingExerciseWithoutItsConfigurationGetsItOnce() {
        doReturn(0L).when(repository).countMetisExerciseConfigs(EXERCISE_ID);

        repository.ensureDefaultConfigurations(withId(new ProgrammingExercise()));

        verify(repository, times(1)).initializeMetisExerciseConfig(EXERCISE_ID);
    }

    @Test
    void aConcurrentCallThatInsertedTheRowFirstIsNotAnError() {
        // the first read finds no row, the other call inserts it before this insert, so the primary key rejects ours
        doReturn(0L, 1L).when(repository).countMetisExerciseConfigs(EXERCISE_ID);
        doThrow(new DataIntegrityViolationException("duplicate key")).when(repository).initializeMetisExerciseConfig(EXERCISE_ID);

        repository.ensureDefaultConfigurations(withId(new ProgrammingExercise()));

        verify(repository, times(1)).initializeMetisExerciseConfig(EXERCISE_ID);
        verify(repository, times(2)).countMetisExerciseConfigs(EXERCISE_ID);
    }

    @Test
    void anIntegrityViolationThatLeavesNoRowBehindIsRethrown() {
        doReturn(0L).when(repository).countMetisExerciseConfigs(EXERCISE_ID);
        DataIntegrityViolationException violation = new DataIntegrityViolationException("foreign key violation");
        doThrow(violation).when(repository).initializeMetisExerciseConfig(EXERCISE_ID);

        assertThatThrownBy(() -> repository.ensureDefaultConfigurations(withId(new ProgrammingExercise()))).isSameAs(violation);
    }
}
