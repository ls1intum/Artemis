package de.tum.cit.aet.artemis.programming.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.security.allowedScopes.AgentScope;
import de.tum.cit.aet.artemis.core.security.allowedScopes.PersonalAccessTokenAuthentication;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;

/**
 * Where the repositories of an exercise are for a coding agent, in isolation from the database and the web layer.
 */
class ProgrammingExerciseRepositoryLocationServiceTest {

    private static final long EXERCISE_ID = 5L;

    private ProgrammingExerciseTestRepository programmingExerciseRepository;

    private ProgrammingExerciseDraftGuardService draftGuard;

    private ProgrammingExerciseRepositoryLocationService service;

    private ProgrammingExercise exercise;

    @BeforeEach
    void setUp() {
        programmingExerciseRepository = mock(ProgrammingExerciseTestRepository.class);
        draftGuard = mock(ProgrammingExerciseDraftGuardService.class);
        exercise = mock(ProgrammingExercise.class);
        when(programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationAndAuxiliaryRepositoriesElseThrow(EXERCISE_ID)).thenReturn(exercise);
        when(programmingExerciseRepository.findBranchByExerciseId(EXERCISE_ID)).thenReturn("main");
        when(draftGuard.repositoryPushBlockedReason(EXERCISE_ID, null, null)).thenReturn(Optional.empty());
        when(exercise.getAuxiliaryRepositories()).thenReturn(Set.of());
        service = new ProgrammingExerciseRepositoryLocationService(programmingExerciseRepository, draftGuard);
        SecurityContextHolder.getContext().setAuthentication(
                new PersonalAccessTokenAuthentication("editor", List.of(new SimpleGrantedAuthority("ROLE_USER")), 1L, Set.of(AgentScope.PROGRAMMING_REPOSITORIES)));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void anExerciseOfAnExamIsNeverLocatedForAToken() {
        when(exercise.isExamExercise()).thenReturn(true);

        assertThatExceptionOfType(AccessForbiddenException.class).isThrownBy(() -> service.locate(EXERCISE_ID, "editor")).withMessageContaining("Exam");
    }

    /**
     * An exercise whose setup failed may lack the address of a repository. The answer then lists the others, rather than failing for all of them.
     */
    @Test
    void aRepositoryWithoutAnAddressIsLeftOut() {
        when(exercise.isExamExercise()).thenReturn(false);
        when(exercise.getRepositoryURI(RepositoryType.TEMPLATE)).thenReturn(null);
        when(exercise.getRepositoryURI(RepositoryType.SOLUTION)).thenReturn(null);
        when(exercise.getRepositoryURI(RepositoryType.TESTS)).thenReturn(new LocalVCRepositoryUri("http://localhost:8080/git/ABC/abc-tests.git"));

        var located = service.locate(EXERCISE_ID, "editor");

        assertThat(located.repositories()).hasSize(1);
        assertThat(located.repositories().getFirst().type()).isEqualTo("tests");
        assertThat(located.repositories().getFirst().cloneUrl()).endsWith("/git/ABC/abc-tests.git");
        assertThat(located.username()).isEqualTo("editor");
        assertThat(located.branch()).isEqualTo("main");
        assertThat(located.writable()).isTrue();
    }

    @Test
    void thePushIsAnnouncedAsBlockedWithItsReason() {
        when(exercise.isExamExercise()).thenReturn(false);
        when(draftGuard.repositoryPushBlockedReason(EXERCISE_ID, null, null)).thenReturn(Optional.of("It is visible to students."));

        var located = service.locate(EXERCISE_ID, "editor");

        assertThat(located.writable()).isFalse();
        assertThat(located.readOnlyReason()).isEqualTo("It is visible to students.");
    }
}
