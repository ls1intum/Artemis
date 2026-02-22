package de.tum.cit.aet.artemis.exercise.service.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.exercise.domain.review.CommentThreadLocationType;
import de.tum.cit.aet.artemis.exercise.service.review.ExerciseReviewRepositoryService.ConsistencyTargetRepositoryUris;
import de.tum.cit.aet.artemis.programming.domain.AuxiliaryRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.SolutionProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.TemplateProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.repository.AuxiliaryRepositoryRepository;
import de.tum.cit.aet.artemis.programming.service.GitService;
import de.tum.cit.aet.artemis.programming.service.localvc.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;

class ExerciseReviewRepositoryServiceTest {

    @Mock
    private ProgrammingExerciseTestRepository programmingExerciseRepository;

    @Mock
    private AuxiliaryRepositoryRepository auxiliaryRepositoryRepository;

    @Mock
    private GitService gitService;

    private ExerciseReviewRepositoryService repositoryService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        repositoryService = new ExerciseReviewRepositoryService(programmingExerciseRepository, auxiliaryRepositoryRepository, gitService);
    }

    @Test
    void shouldReturnNullWithoutRepositoryAccessWhenResolvingCommitShaForProblemStatement() {
        String commitSha = repositoryService.resolveLatestCommitSha(CommentThreadLocationType.PROBLEM_STATEMENT, null, 42L);

        assertThat(commitSha).isNull();
        verifyNoInteractions(programmingExerciseRepository, auxiliaryRepositoryRepository, gitService);
    }

    @Test
    void shouldThrowBadRequestWhenResolvingCommitShaForNonProgrammingExercise() {
        when(programmingExerciseRepository.findWithTemplateAndSolutionParticipationAndAuxiliaryRepositoriesById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> repositoryService.resolveLatestCommitSha(CommentThreadLocationType.TEMPLATE_REPO, null, 42L)).isInstanceOf(BadRequestAlertException.class)
                .extracting("errorKey").isEqualTo("exerciseNotProgramming");
    }

    @Test
    void shouldResolveTemplateCommitShaFromTemplateParticipationUri() {
        ProgrammingExercise exercise = createProgrammingExercise(1L);
        TemplateProgrammingExerciseParticipation templateParticipation = new TemplateProgrammingExerciseParticipation();
        templateParticipation.setRepositoryUri("http://localhost/git/EX1/ex1-template.git");
        exercise.setTemplateParticipation(templateParticipation);

        when(programmingExerciseRepository.findWithTemplateAndSolutionParticipationAndAuxiliaryRepositoriesById(1L)).thenReturn(Optional.of(exercise));
        when(gitService.getLastCommitHash(any(LocalVCRepositoryUri.class))).thenReturn("template-hash");

        String commitSha = repositoryService.resolveLatestCommitSha(CommentThreadLocationType.TEMPLATE_REPO, null, 1L);

        assertThat(commitSha).isEqualTo("template-hash");
        verify(gitService).getLastCommitHash(any(LocalVCRepositoryUri.class));
    }

    @Test
    void shouldResolveSolutionCommitShaFromSolutionParticipationUri() {
        ProgrammingExercise exercise = createProgrammingExercise(2L);
        SolutionProgrammingExerciseParticipation solutionParticipation = new SolutionProgrammingExerciseParticipation();
        solutionParticipation.setRepositoryUri("http://localhost/git/EX1/ex1-solution.git");
        exercise.setSolutionParticipation(solutionParticipation);

        when(programmingExerciseRepository.findWithTemplateAndSolutionParticipationAndAuxiliaryRepositoriesById(2L)).thenReturn(Optional.of(exercise));
        when(gitService.getLastCommitHash(any(LocalVCRepositoryUri.class))).thenReturn("solution-hash");

        String commitSha = repositoryService.resolveLatestCommitSha(CommentThreadLocationType.SOLUTION_REPO, null, 2L);

        assertThat(commitSha).isEqualTo("solution-hash");
        verify(gitService).getLastCommitHash(any(LocalVCRepositoryUri.class));
    }

    @Test
    void shouldResolveTestCommitShaFromTestRepositoryUri() {
        ProgrammingExercise exercise = createProgrammingExercise(3L);
        exercise.setTestRepositoryUri("http://localhost/git/EX1/ex1-tests.git");

        when(programmingExerciseRepository.findWithTemplateAndSolutionParticipationAndAuxiliaryRepositoriesById(3L)).thenReturn(Optional.of(exercise));
        when(gitService.getLastCommitHash(any(LocalVCRepositoryUri.class))).thenReturn("test-hash");

        String commitSha = repositoryService.resolveLatestCommitSha(CommentThreadLocationType.TEST_REPO, null, 3L);

        assertThat(commitSha).isEqualTo("test-hash");
        verify(gitService).getLastCommitHash(any(LocalVCRepositoryUri.class));
    }

    @Test
    void shouldReturnNullWhenRepositoryUriIsMissingDuringCommitShaResolution() {
        ProgrammingExercise exercise = createProgrammingExercise(4L);
        when(programmingExerciseRepository.findWithTemplateAndSolutionParticipationAndAuxiliaryRepositoriesById(4L)).thenReturn(Optional.of(exercise));

        String commitSha = repositoryService.resolveLatestCommitSha(CommentThreadLocationType.TEMPLATE_REPO, null, 4L);

        assertThat(commitSha).isNull();
        verify(gitService, never()).getLastCommitHash(any(LocalVCRepositoryUri.class));
    }

    @Test
    void shouldThrowBadRequestWhenAuxiliaryRepositoryIdIsMissingForCommitShaResolution() {
        ProgrammingExercise exercise = createProgrammingExercise(5L);
        when(programmingExerciseRepository.findWithTemplateAndSolutionParticipationAndAuxiliaryRepositoriesById(5L)).thenReturn(Optional.of(exercise));

        assertThatThrownBy(() -> repositoryService.resolveLatestCommitSha(CommentThreadLocationType.AUXILIARY_REPO, null, 5L)).isInstanceOf(BadRequestAlertException.class)
                .extracting("errorKey").isEqualTo("auxiliaryRepositoryMissing");
    }

    @Test
    void shouldThrowEntityNotFoundWhenAuxiliaryRepositoryDoesNotExistDuringCommitShaResolution() {
        ProgrammingExercise exercise = createProgrammingExercise(6L);
        when(programmingExerciseRepository.findWithTemplateAndSolutionParticipationAndAuxiliaryRepositoriesById(6L)).thenReturn(Optional.of(exercise));
        when(auxiliaryRepositoryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> repositoryService.resolveLatestCommitSha(CommentThreadLocationType.AUXILIARY_REPO, 99L, 6L)).isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void shouldThrowBadRequestWhenAuxiliaryRepositoryDoesNotBelongToExercise() {
        ProgrammingExercise exercise = createProgrammingExercise(7L);
        ProgrammingExercise otherExercise = createProgrammingExercise(8L);

        AuxiliaryRepository auxiliaryRepository = new AuxiliaryRepository();
        auxiliaryRepository.setId(99L);
        auxiliaryRepository.setExercise(otherExercise);
        auxiliaryRepository.setRepositoryUri("http://localhost/git/EX1/ex1-aux.git");

        when(programmingExerciseRepository.findWithTemplateAndSolutionParticipationAndAuxiliaryRepositoriesById(7L)).thenReturn(Optional.of(exercise));
        when(auxiliaryRepositoryRepository.findById(99L)).thenReturn(Optional.of(auxiliaryRepository));

        assertThatThrownBy(() -> repositoryService.resolveLatestCommitSha(CommentThreadLocationType.AUXILIARY_REPO, 99L, 7L)).isInstanceOf(BadRequestAlertException.class)
                .extracting("errorKey").isEqualTo("auxiliaryRepositoryMismatch");
    }

    @Test
    void shouldResolveAuxiliaryCommitShaWhenAuxiliaryRepositoryIsValid() {
        ProgrammingExercise exercise = createProgrammingExercise(9L);
        AuxiliaryRepository auxiliaryRepository = new AuxiliaryRepository();
        auxiliaryRepository.setId(12L);
        auxiliaryRepository.setExercise(exercise);
        auxiliaryRepository.setRepositoryUri("http://localhost/git/EX1/ex1-aux.git");

        when(programmingExerciseRepository.findWithTemplateAndSolutionParticipationAndAuxiliaryRepositoriesById(9L)).thenReturn(Optional.of(exercise));
        when(auxiliaryRepositoryRepository.findById(12L)).thenReturn(Optional.of(auxiliaryRepository));
        when(gitService.getLastCommitHash(any(LocalVCRepositoryUri.class))).thenReturn("aux-hash");

        String commitSha = repositoryService.resolveLatestCommitSha(CommentThreadLocationType.AUXILIARY_REPO, 12L, 9L);

        assertThat(commitSha).isEqualTo("aux-hash");
    }

    @Test
    void shouldReturnEmptyTargetRepositoryUrisWhenExerciseIsMissing() {
        when(programmingExerciseRepository.findWithTemplateAndSolutionParticipationAndAuxiliaryRepositoriesById(42L)).thenReturn(Optional.empty());

        ConsistencyTargetRepositoryUris uris = repositoryService.resolveTargetRepositoryUris(42L);

        assertThat(uris.repositoryUrisByTargetType()).isEmpty();
        assertThat(uris.auxiliaryRepositoryUrisById()).isEmpty();
    }

    @Test
    void shouldResolveAllSupportedTargetRepositoryUrisWhenAvailable() {
        ProgrammingExercise exercise = createProgrammingExercise(10L);

        TemplateProgrammingExerciseParticipation templateParticipation = new TemplateProgrammingExerciseParticipation();
        templateParticipation.setRepositoryUri("http://localhost/git/EX1/ex1-template.git");
        exercise.setTemplateParticipation(templateParticipation);

        SolutionProgrammingExerciseParticipation solutionParticipation = new SolutionProgrammingExerciseParticipation();
        solutionParticipation.setRepositoryUri("http://localhost/git/EX1/ex1-solution.git");
        exercise.setSolutionParticipation(solutionParticipation);

        exercise.setTestRepositoryUri("http://localhost/git/EX1/ex1-tests.git");

        AuxiliaryRepository auxiliaryRepository = new AuxiliaryRepository();
        auxiliaryRepository.setId(77L);
        auxiliaryRepository.setRepositoryUri("http://localhost/git/EX1/ex1-aux.git");
        auxiliaryRepository.setExercise(exercise);
        exercise.setAuxiliaryRepositories(List.of(auxiliaryRepository));

        when(programmingExerciseRepository.findWithTemplateAndSolutionParticipationAndAuxiliaryRepositoriesById(10L)).thenReturn(Optional.of(exercise));

        ConsistencyTargetRepositoryUris uris = repositoryService.resolveTargetRepositoryUris(10L);

        assertThat(uris.repositoryUrisByTargetType()).containsKeys(CommentThreadLocationType.TEMPLATE_REPO, CommentThreadLocationType.SOLUTION_REPO,
                CommentThreadLocationType.TEST_REPO);
        assertThat(uris.auxiliaryRepositoryUrisById()).containsKey(77L);
    }

    @Test
    void shouldReturnValidationErrorWhenRepositoryUriIsMissingForFileValidation() {
        ConsistencyTargetRepositoryUris uris = new ConsistencyTargetRepositoryUris(Map.of(), Map.of());

        Optional<String> validationError = repositoryService.validateFileExists(CommentThreadLocationType.TEMPLATE_REPO, null, "src/main/App.java", uris);

        assertThat(validationError).contains("repository URI for TEMPLATE_REPO is missing");
    }

    @Test
    void shouldReturnValidationErrorWhenFileValidationFailsDueToGitError() {
        LocalVCRepositoryUri templateUri = new LocalVCRepositoryUri("http://localhost/git/EX1/ex1-template.git");
        ConsistencyTargetRepositoryUris uris = new ConsistencyTargetRepositoryUris(Map.of(CommentThreadLocationType.TEMPLATE_REPO, templateUri), Map.of());
        when(gitService.getBareRepository(eq(templateUri), eq(false))).thenThrow(new RuntimeException("boom"));

        Optional<String> validationError = repositoryService.validateFileExists(CommentThreadLocationType.TEMPLATE_REPO, null, "src/main/App.java", uris);

        assertThat(validationError).isPresent();
        assertThat(validationError.get()).contains("file existence check failed");
        assertThat(validationError.get()).contains("boom");
    }

    @Test
    void shouldResolveAuxiliaryRepositoryUriWhenValidatingAuxiliaryRepositoryFile() {
        LocalVCRepositoryUri auxiliaryUri = new LocalVCRepositoryUri("http://localhost/git/EX1/ex1-aux.git");
        ConsistencyTargetRepositoryUris uris = new ConsistencyTargetRepositoryUris(Map.of(), Map.of(77L, auxiliaryUri));
        when(gitService.getBareRepository(eq(auxiliaryUri), eq(false))).thenThrow(new RuntimeException("boom"));

        Optional<String> validationError = repositoryService.validateFileExists(CommentThreadLocationType.AUXILIARY_REPO, 77L, "src/main/App.java", uris);

        assertThat(validationError).isPresent();
        assertThat(validationError.get()).contains("file existence check failed");
        assertThat(validationError.get()).contains("boom");
        verify(gitService).getBareRepository(eq(auxiliaryUri), eq(false));
    }

    private static ProgrammingExercise createProgrammingExercise(long id) {
        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setId(id);
        return exercise;
    }
}
