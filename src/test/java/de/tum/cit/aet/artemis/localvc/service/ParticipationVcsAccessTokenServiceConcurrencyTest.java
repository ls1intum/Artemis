package de.tum.cit.aet.artemis.localvc.service;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.exercise.repository.TeamRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.repository.ParticipationVCSAccessTokenRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseStudentParticipationTestRepository;

/**
 * Two requests creating the access token for the same participation at the same moment.
 * <p>
 * createVcsAccessTokenForUserAndParticipationIdOrElseThrow asks whether a token exists and inserts one when it finds
 * none, and those two steps are not atomic. The request that loses the race violates the unique constraint on
 * (user_id, participation_id), and without the translation below it would answer 500 while its twin succeeds - the same
 * status the sequential duplicate used to produce.
 * <p>
 * A plain unit test on purpose: the collision has to fall between the service's own read and its insert, which a real
 * database cannot be made to do on demand.
 */
@ExtendWith(MockitoExtension.class)
class ParticipationVcsAccessTokenServiceConcurrencyTest {

    private static final long PARTICIPATION_ID = 42L;

    private static final long USER_ID = 5L;

    private static final String USER_LOGIN = "student1";

    @Mock
    private ParticipationVCSAccessTokenRepository participationVcsAccessTokenRepository;

    @Mock
    private ProgrammingExerciseStudentParticipationTestRepository programmingExerciseStudentParticipationRepository;

    @Mock
    private TeamRepository teamRepository;

    @InjectMocks
    private ParticipationVcsAccessTokenService participationVcsAccessTokenService;

    private static User user() {
        User user = new User();
        user.setId(USER_ID);
        user.setLogin(USER_LOGIN);
        return user;
    }

    private static ProgrammingExerciseStudentParticipation participation() {
        ProgrammingExerciseStudentParticipation participation = new ProgrammingExerciseStudentParticipation();
        participation.setId(PARTICIPATION_ID);
        participation.setExercise(new ProgrammingExercise());
        participation.setParticipant(user());
        return participation;
    }

    @Test
    void theRequestLosingTheRaceGetsAConflict() {
        when(programmingExerciseStudentParticipationRepository.findByIdElseThrow(PARTICIPATION_ID)).thenReturn(participation());
        // Nothing on the look-up - the same view the winning request had - and the winner's row once its insert landed.
        when(participationVcsAccessTokenRepository.existsByUserIdAndParticipationId(USER_ID, PARTICIPATION_ID)).thenReturn(false, true);
        when(participationVcsAccessTokenRepository.save(any())).thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"));

        assertThatExceptionOfType(ConflictException.class).as("the loser must be told that the token already exists, not that the server failed")
                .isThrownBy(() -> participationVcsAccessTokenService.createVcsAccessTokenForUserAndParticipationIdOrElseThrow(user(), PARTICIPATION_ID));
    }

    @Test
    void aViolationWithNoTokenBehindItStillFails() {
        when(programmingExerciseStudentParticipationRepository.findByIdElseThrow(PARTICIPATION_ID)).thenReturn(participation());
        // Still no token on the second look, so a lost race does not explain the violation.
        when(participationVcsAccessTokenRepository.existsByUserIdAndParticipationId(anyLong(), anyLong())).thenReturn(false);
        when(participationVcsAccessTokenRepository.save(any())).thenThrow(new DataIntegrityViolationException("not-null constraint violated"));

        assertThatExceptionOfType(DataIntegrityViolationException.class)
                .isThrownBy(() -> participationVcsAccessTokenService.createVcsAccessTokenForUserAndParticipationIdOrElseThrow(user(), PARTICIPATION_ID));
    }
}
