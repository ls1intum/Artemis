package de.tum.cit.aet.artemis.exercise.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import de.tum.cit.aet.artemis.exercise.domain.InitializationState;
import de.tum.cit.aet.artemis.exercise.domain.Team;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.test_repository.StudentParticipationTestRepository;
import de.tum.cit.aet.artemis.exercise.test_repository.SubmissionTestRepository;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;

/**
 * Two members of the same team starting an exercise at the same moment.
 * <p>
 * startExercise looks for an existing participation and creates one when it finds none, and those two steps are not
 * atomic. For a team exercise the overlap is the ordinary case rather than a corner one - two teammates opening the
 * exercise together is how a team starts - and the request that loses the race used to violate the unique constraint on
 * (team_id, exercise_id, initialization_state) and return a 500 while its twin succeeded.
 * <p>
 * A plain unit test on purpose: the collision has to fall between the service's own read and its insert, which a real
 * database cannot be made to do on demand.
 */
@ExtendWith(MockitoExtension.class)
class ParticipationServiceConcurrencyTest {

    private static final long EXERCISE_ID = 7L;

    private static final long TEAM_ID = 11L;

    @Mock
    private StudentParticipationTestRepository participationRepository;

    @Mock
    private SubmissionTestRepository submissionRepository;

    @InjectMocks
    private ParticipationService participationService;

    private record Fixture(ParticipationService service, StudentParticipationTestRepository participationRepository) {
    }

    private Fixture fixture() {
        // A real service built by Mockito's constructor injection: the collaborators this path does not touch stay
        // unset, which is what keeps the test to the branch under test. Mocking the service itself would not run it.
        return new Fixture(participationService, participationRepository);
    }

    private static ModelingExercise exercise() {
        ModelingExercise exercise = new ModelingExercise();
        exercise.setId(EXERCISE_ID);
        return exercise;
    }

    private static Team team() {
        Team team = new Team();
        team.setId(TEAM_ID);
        return team;
    }

    private static StudentParticipation existingParticipation(ModelingExercise exercise) {
        StudentParticipation participation = new StudentParticipation();
        participation.setId(99L);
        participation.setExercise(exercise);
        participation.setInitializationState(InitializationState.INITIALIZED);
        return participation;
    }

    @Test
    void aTeammateLosingTheRaceGetsTheTeamsParticipation() {
        var fixture = fixture();
        ModelingExercise exercise = exercise();
        StudentParticipation winners = existingParticipation(exercise);
        // Nothing on the first look - the same view the winning request had - and the winner's row once its insert landed.
        when(fixture.participationRepository().findWithEagerSubmissionsAndTeamStudentsByExerciseIdAndTeamId(EXERCISE_ID, TEAM_ID)).thenReturn(Optional.empty(),
                Optional.of(winners));
        when(fixture.participationRepository().saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StudentParticipation result = fixture.service().startExercise(exercise, team(), false);

        assertThat(result).as("the loser must receive the participation its teammate created, not an error").isNotNull();
        assertThat(result.getId()).isEqualTo(winners.getId());
    }

    @Test
    void aViolationWithNothingToFetchStillFails() {
        var fixture = fixture();
        ModelingExercise exercise = exercise();
        // Still nothing on the second look, so a lost race does not explain the violation.
        when(fixture.participationRepository().findWithEagerSubmissionsAndTeamStudentsByExerciseIdAndTeamId(anyLong(), anyLong())).thenReturn(Optional.empty());
        when(fixture.participationRepository().saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("not-null constraint violated"));

        assertThatExceptionOfType(DataIntegrityViolationException.class).isThrownBy(() -> fixture.service().startExercise(exercise, team(), false));
    }

    @Test
    void aTeammateLosingTheRaceDoesNotAddASecondInitialSubmission() {
        var fixture = fixture();
        ModelingExercise exercise = exercise();
        StudentParticipation winners = existingParticipation(exercise);
        when(fixture.participationRepository().findWithEagerSubmissionsAndTeamStudentsByExerciseIdAndTeamId(EXERCISE_ID, TEAM_ID)).thenReturn(Optional.empty(),
                Optional.of(winners));
        when(fixture.participationRepository().saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"))
                .thenAnswer(invocation -> invocation.getArgument(0));
        // The winning request has already created the initial submission on the participation the loser now receives.
        when(submissionRepository.existsByParticipationId(winners.getId())).thenReturn(true);

        fixture.service().startExercise(exercise, team(), true);

        // The loser found no participation of its own, so without consulting the submission it would write a second
        // initial submission onto its teammate's participation.
        verify(submissionRepository, never()).initializeSubmission(any(), any(), any());
    }
}
