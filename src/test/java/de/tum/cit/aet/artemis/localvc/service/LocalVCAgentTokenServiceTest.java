package de.tum.cit.aet.artemis.localvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.security.allowedScopes.AgentScope;
import de.tum.cit.aet.artemis.core.security.allowedScopes.PersonalAccessTokenAuthentication;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.localvc.exception.LocalVCAuthException;
import de.tum.cit.aet.artemis.localvc.exception.LocalVCForbiddenException;
import de.tum.cit.aet.artemis.programming.dto.GitRepositoryAccessDTO;
import de.tum.cit.aet.artemis.programming.service.AuxiliaryRepositoryService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseDraftGuardService;
import de.tum.cit.aet.artemis.programming.web.repository.RepositoryActionType;

/**
 * The rules that narrow a personal access token on git, in isolation from the repositories and the database.
 */
class LocalVCAgentTokenServiceTest {

    private static final long EXERCISE_ID = 7L;

    private AuxiliaryRepositoryService auxiliaryRepositoryService;

    private ProgrammingExerciseDraftGuardService draftGuard;

    private AuthorizationCheckService authorizationCheckService;

    private final User owner = new User();

    private LocalVCAgentTokenService service;

    private final PersonalAccessTokenAuthentication token = new PersonalAccessTokenAuthentication("editor", List.of(new SimpleGrantedAuthority("ROLE_USER")), 1L,
            Set.of(AgentScope.PROGRAMMING_REPOSITORIES));

    @BeforeEach
    void setUp() {
        auxiliaryRepositoryService = mock(AuxiliaryRepositoryService.class);
        draftGuard = mock(ProgrammingExerciseDraftGuardService.class);
        authorizationCheckService = mock(AuthorizationCheckService.class);
        when(authorizationCheckService.isAtLeastEditorInCourse(3L, owner)).thenReturn(true);
        when(draftGuard.repositoryPushBlockedReason(EXERCISE_ID, null, null)).thenReturn(Optional.empty());
        service = new LocalVCAgentTokenService(Optional.empty(), auxiliaryRepositoryService, draftGuard, authorizationCheckService);
    }

    private static GitRepositoryAccessDTO exercise(Long examId) {
        return new GitRepositoryAccessDTO(EXERCISE_ID, 3L, ExerciseMode.INDIVIDUAL, true, null, null, null, null, examId, null, null);
    }

    @Test
    void anExamExerciseIsNeverOpened() {
        assertThatExceptionOfType(LocalVCForbiddenException.class).isThrownBy(() -> service.authorize(token, owner, exercise(5L), "solution", RepositoryActionType.READ))
                .withMessageContaining("Exam");
    }

    @Test
    void anAuxiliaryRepositoryIsOpenedAndTheRepositoryOfAStudentIsNot() {
        when(auxiliaryRepositoryService.isAuxiliaryRepositoryOfExercise("extras", EXERCISE_ID)).thenReturn(true);

        assertThatCode(() -> service.authorize(token, owner, exercise(null), "extras", RepositoryActionType.WRITE)).doesNotThrowAnyException();
        assertThatExceptionOfType(LocalVCForbiddenException.class).isThrownBy(() -> service.authorize(token, owner, exercise(null), "student1", RepositoryActionType.READ))
                .withMessageContaining("not those of students");
    }

    @Test
    void aBlockedPushIsRefusedWithTheReasonAndAReadIsNot() {
        when(draftGuard.repositoryPushBlockedReason(EXERCISE_ID, null, null)).thenReturn(Optional.of("It is visible to students."));

        assertThatExceptionOfType(LocalVCForbiddenException.class).isThrownBy(() -> service.authorize(token, owner, exercise(null), "tests", RepositoryActionType.WRITE))
                .withMessage("It is visible to students.");
        assertThatCode(() -> service.authorize(token, owner, exercise(null), "tests", RepositoryActionType.READ)).doesNotThrowAnyException();
    }

    @Test
    void aTokenOfAnOwnerBelowEditorOpensNothingEvenForReading() {
        when(authorizationCheckService.isAtLeastEditorInCourse(3L, owner)).thenReturn(false);

        assertThatExceptionOfType(LocalVCForbiddenException.class).isThrownBy(() -> service.authorize(token, owner, exercise(null), "tests", RepositoryActionType.READ))
                .withMessageContaining("editor");
    }

    @Test
    void withoutAgentAccessEveryTokenIsRejected() {
        User user = new User();
        user.setLogin("editor");

        assertThatExceptionOfType(LocalVCAuthException.class).isThrownBy(() -> service.authenticate(user, "art_pat_" + "a".repeat(43)));
    }

    @Test
    void onlyTheTokenPrefixMarksAnAgentToken() {
        assertThat(LocalVCAgentTokenService.isAgentToken("art_pat_abc")).isTrue();
        assertThat(LocalVCAgentTokenService.isAgentToken("vcpat-abc")).isFalse();
        assertThat(LocalVCAgentTokenService.isAgentToken("00000000")).isFalse();
    }
}
