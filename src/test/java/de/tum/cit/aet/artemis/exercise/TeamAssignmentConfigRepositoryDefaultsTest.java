package de.tum.cit.aet.artemis.exercise;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.TeamAssignmentConfig;
import de.tum.cit.aet.artemis.exercise.repository.TeamAssignmentConfigRepository;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

/**
 * Pins the default methods of the team assignment configuration repository that need a failing or racing database to
 * reach: the repair after a concurrent insert, the lost row and the batching of the bulk read. The abstract queries are
 * stubbed, the default methods run for real. The behavior against a real database is covered by
 * {@link ExerciseConfigurationLoadProfileTest}.
 */
class TeamAssignmentConfigRepositoryDefaultsTest {

    private static final long EXERCISE_ID = 17L;

    private TeamAssignmentConfigRepository repository;

    private TextExercise exercise;

    @BeforeEach
    void setup() {
        repository = mock(TeamAssignmentConfigRepository.class, CALLS_REAL_METHODS);
        exercise = exerciseWithId(EXERCISE_ID);
    }

    private static TextExercise exerciseWithId(long id) {
        TextExercise result = new TextExercise();
        result.setId(id);
        return result;
    }

    private static TeamAssignmentConfig configOf(long exerciseId, int minTeamSize, int maxTeamSize) {
        TeamAssignmentConfig config = new TeamAssignmentConfig();
        config.setMinTeamSize(minTeamSize);
        config.setMaxTeamSize(maxTeamSize);
        // the key is read from the database column, so it is set directly
        ReflectionTestUtils.setField(config, "exerciseId", exerciseId);
        return config;
    }

    @Test
    void ensuringARowThatExistsInsertsNothing() {
        doReturn(true).when(repository).existsByExerciseId(EXERCISE_ID);

        repository.ensureExistsFor(EXERCISE_ID);

        verify(repository, never()).insertDefaultsFor(anyLong());
    }

    @Test
    void ensuringAMissingRowInsertsTheDefaultsOnce() {
        doReturn(false).when(repository).existsByExerciseId(EXERCISE_ID);

        repository.ensureExistsFor(EXERCISE_ID);

        verify(repository, times(1)).insertDefaultsFor(EXERCISE_ID);
    }

    @Test
    void aConcurrentSaveThatInsertedTheRowFirstIsNotAnError() {
        // the first read finds no row, the other save inserts it before this insert, so the unique key rejects ours
        doReturn(false, true).when(repository).existsByExerciseId(EXERCISE_ID);
        doThrow(new DataIntegrityViolationException("duplicate key")).when(repository).insertDefaultsFor(EXERCISE_ID);

        repository.ensureExistsFor(EXERCISE_ID);

        verify(repository, times(1)).insertDefaultsFor(EXERCISE_ID);
        verify(repository, times(2)).existsByExerciseId(EXERCISE_ID);
    }

    @Test
    void anIntegrityViolationThatLeavesNoRowBehindIsRethrown() {
        doReturn(false).when(repository).existsByExerciseId(EXERCISE_ID);
        DataIntegrityViolationException violation = new DataIntegrityViolationException("foreign key violation");
        doThrow(violation).when(repository).insertDefaultsFor(EXERCISE_ID);

        assertThatThrownBy(() -> repository.ensureExistsFor(EXERCISE_ID)).isSameAs(violation);
    }

    @Test
    void applyingRequestedSizesPassesBothValuesToTheUpdateAndReturnsTheStoredRow() {
        doReturn(true).when(repository).existsByExerciseId(EXERCISE_ID);
        doReturn(1).when(repository).updateSizes(anyLong(), anyInt(), anyInt());
        TeamAssignmentConfig stored = configOf(EXERCISE_ID, 2, 5);
        doReturn(Optional.of(stored)).when(repository).findByExerciseId(EXERCISE_ID);

        TeamAssignmentConfig result = repository.applyTo(exercise, configOf(EXERCISE_ID, 2, 5));

        verify(repository).updateSizes(EXERCISE_ID, 2, 5);
        assertThat(result).isSameAs(stored);
        assertThat(exercise.getStoredTeamAssignmentConfig()).isSameAs(stored);
    }

    @Test
    void initializingWithoutSettingsKeepsTheStoredOnesWithoutUpdatingThem() {
        doReturn(true).when(repository).existsByExerciseId(EXERCISE_ID);
        TeamAssignmentConfig stored = configOf(EXERCISE_ID, 3, 4);
        doReturn(Optional.of(stored)).when(repository).findByExerciseId(EXERCISE_ID);

        TeamAssignmentConfig result = repository.initializeFor(exercise, null);

        assertThat(result).isSameAs(stored);
        verify(repository, never()).updateSizes(anyLong(), anyInt(), anyInt());
    }

    @Test
    void applyingSizesToARowThatVanishedIsRejectedBeforeAnythingIsAttached() {
        doReturn(true).when(repository).existsByExerciseId(EXERCISE_ID);
        // the row was deleted between the check and the update, so no row is updated
        doReturn(0).when(repository).updateSizes(anyLong(), anyInt(), anyInt());

        assertThatThrownBy(() -> repository.applyTo(exercise, configOf(EXERCISE_ID, 2, 3))).isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining(String.valueOf(EXERCISE_ID));

        verify(repository, never()).findByExerciseId(anyLong());
    }

    @Test
    void anExerciseWhoseRowCannotBeReadAfterwardsIsRejected() {
        doReturn(true).when(repository).existsByExerciseId(EXERCISE_ID);
        doReturn(Optional.empty()).when(repository).findByExerciseId(EXERCISE_ID);
        exercise.setTeamAssignmentConfig(configOf(EXERCISE_ID, 2, 3));

        assertThatThrownBy(() -> repository.applyTo(exercise, null)).isInstanceOf(EntityNotFoundException.class).hasMessageContaining(String.valueOf(EXERCISE_ID));

        assertThat(exercise.getStoredTeamAssignmentConfig()).as("the slot is emptied rather than left stale").isNull();
    }

    @Test
    void attachingSeveralExercisesReadsOneBatchPerThousandAndMatchesByKey() {
        int count = 2 * TeamAssignmentConfigRepository.ATTACH_BATCH_SIZE + 500;
        List<Exercise> exercises = new ArrayList<>();
        for (long id = 1; id <= count; id++) {
            exercises.add(exerciseWithId(id));
        }
        // a stale slot is emptied when no row is read for it
        exercises.getFirst().setTeamAssignmentConfig(configOf(1L, 6, 7));
        // an exercise that is not persisted yet does not take part in the read and keeps its slot
        TextExercise unsaved = new TextExercise();
        TeamAssignmentConfig unsavedSlot = new TeamAssignmentConfig();
        unsaved.setTeamAssignmentConfig(unsavedSlot);
        exercises.add(unsaved);
        // the stub returns the same two rows for every batch; only the exercise a row belongs to takes it
        doReturn(List.of(configOf(3L, 2, 3), configOf(2001L, 4, 5))).when(repository).findAllByExerciseIdIn(anyCollection());

        repository.attachTo(exercises);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<Long>> batches = ArgumentCaptor.forClass(Collection.class);
        verify(repository, times(3)).findAllByExerciseIdIn(batches.capture());
        assertThat(batches.getAllValues()).extracting(Collection::size).containsExactlyInAnyOrder(1000, 1000, 500);
        assertThat(batches.getAllValues().stream().flatMap(Collection::stream)).hasSize(count).doesNotHaveDuplicates();
        assertThat(exercises.get(2).getStoredTeamAssignmentConfig().getMinTeamSize()).isEqualTo(2);
        assertThat(exercises.get(2000).getStoredTeamAssignmentConfig().getMaxTeamSize()).isEqualTo(5);
        assertThat(exercises.getFirst().getStoredTeamAssignmentConfig()).isNull();
        assertThat(exercises.get(1).getStoredTeamAssignmentConfig()).isNull();
        assertThat(unsaved.getStoredTeamAssignmentConfig()).isSameAs(unsavedSlot);
    }

    @Test
    void attachingNoExercisesReadsNothing() {
        repository.attachTo(List.of());

        verify(repository, never()).findAllByExerciseIdIn(anyCollection());
    }
}
