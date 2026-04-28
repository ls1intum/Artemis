package de.tum.cit.aet.artemis.hyperion.service.codegeneration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.test_repository.ResultTestRepository;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.ContinuousIntegrationException;
import de.tum.cit.aet.artemis.exercise.service.ExerciseVersionService;
import de.tum.cit.aet.artemis.hyperion.domain.ArtifactType;
import de.tum.cit.aet.artemis.hyperion.domain.ConsistencyIssueCategory;
import de.tum.cit.aet.artemis.hyperion.domain.Severity;
import de.tum.cit.aet.artemis.hyperion.dto.ArtifactLocationDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ConsistencyCheckResponseDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ConsistencyIssueDTO;
import de.tum.cit.aet.artemis.hyperion.dto.GeneratedFileDTO;
import de.tum.cit.aet.artemis.hyperion.dto.HyperionCodeGenerationEventDTO;
import de.tum.cit.aet.artemis.hyperion.service.HyperionConsistencyCheckService;
import de.tum.cit.aet.artemis.hyperion.service.HyperionProgrammingExerciseContextRendererService;
import de.tum.cit.aet.artemis.programming.domain.File;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.SolutionProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.build.BuildLogEntry;
import de.tum.cit.aet.artemis.programming.repository.SolutionProgrammingExerciseParticipationRepository;
import de.tum.cit.aet.artemis.programming.service.GitService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseParticipationService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingSubmissionService;
import de.tum.cit.aet.artemis.programming.service.RepositoryService;
import de.tum.cit.aet.artemis.programming.service.ci.ContinuousIntegrationTriggerService;
import de.tum.cit.aet.artemis.programming.service.localvc.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingSubmissionTestRepository;
import de.tum.cit.aet.artemis.programming.test_repository.TemplateProgrammingExerciseParticipationTestRepository;

class HyperionCodeGenerationExecutionServiceTest {

    @Mock
    private GitService gitService;

    @Mock
    private RepositoryService repositoryService;

    @Mock
    private SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository;

    @Mock
    private TemplateProgrammingExerciseParticipationTestRepository templateProgrammingExerciseParticipationRepository;

    @Mock
    private ProgrammingSubmissionTestRepository programmingSubmissionRepository;

    @Mock
    private ResultTestRepository resultRepository;

    @Mock
    private ContinuousIntegrationTriggerService continuousIntegrationTriggerService;

    @Mock
    private ProgrammingExerciseParticipationService programmingExerciseParticipationService;

    @Mock
    private HyperionProgrammingExerciseContextRendererService repositoryStructureService;

    @Mock
    private HyperionSolutionRepositoryService solutionStrategy;

    @Mock
    private HyperionTemplateRepositoryService templateStrategy;

    @Mock
    private HyperionTestRepositoryService testStrategy;

    @Mock
    private ProgrammingSubmissionService programmingSubmissionService;

    @Mock
    private HyperionConsistencyCheckService consistencyCheckService;

    @Mock
    private ExerciseVersionService exerciseVersionService;

    private HyperionCodeGenerationExecutionService service;

    private User user;

    private ProgrammingExercise exercise;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        this.service = new HyperionCodeGenerationExecutionService("main", gitService, repositoryService, solutionProgrammingExerciseParticipationRepository,
                templateProgrammingExerciseParticipationRepository, programmingSubmissionRepository, resultRepository, continuousIntegrationTriggerService,
                programmingExerciseParticipationService, repositoryStructureService, solutionStrategy, templateStrategy, testStrategy, programmingSubmissionService,
                consistencyCheckService, exerciseVersionService);

        this.user = new User();
        user.setLogin("testuser");

        this.exercise = new ProgrammingExercise();
        exercise.setId(1L);
        exercise.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        exercise.setProblemStatement("Implement sorting algorithm");
    }

    @Test
    void resolveStrategy_withSolutionRepositoryType_returnsSolutionStrategy() {
        HyperionCodeGenerationService result = ReflectionTestUtils.invokeMethod(service, "resolveStrategy", RepositoryType.SOLUTION);

        assertThat(result).isEqualTo(solutionStrategy);
    }

    @Test
    void resolveStrategy_withTemplateRepositoryType_returnsTemplateStrategy() {
        HyperionCodeGenerationService result = ReflectionTestUtils.invokeMethod(service, "resolveStrategy", RepositoryType.TEMPLATE);

        assertThat(result).isEqualTo(templateStrategy);
    }

    @Test
    void resolveStrategy_withTestsRepositoryType_returnsTestStrategy() {
        HyperionCodeGenerationService result = ReflectionTestUtils.invokeMethod(service, "resolveStrategy", RepositoryType.TESTS);

        assertThat(result).isEqualTo(testStrategy);
    }

    @Test
    void resolveStrategy_withUnsupportedRepositoryType_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service, "resolveStrategy", RepositoryType.AUXILIARY)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported repository type for code generation: auxiliary");
    }

    @Test
    void generateAndCompileCode_withNullRepositoryUri_returnsNull() {
        HyperionCodeGenerationEventPublisher publisher = mock(HyperionCodeGenerationEventPublisher.class);

        Result result = service.generateAndCompileCode(exercise, user, 1L, RepositoryType.SOLUTION, publisher);

        assertThat(result).isNull();
        verify(publisher, times(1)).error(anyString());
    }

    @Test
    void generateAndCompileCode_withSuccessfulRun_createsExerciseVersion() throws Exception {
        HyperionCodeGenerationEventPublisher publisher = mock(HyperionCodeGenerationEventPublisher.class);
        Repository repository = mock(Repository.class);
        String originalCommitId = "orig-hash";
        String newCommitId = "new-hash";
        SolutionProgrammingExerciseParticipation solutionParticipation = new SolutionProgrammingExerciseParticipation();
        solutionParticipation.setId(99L);
        solutionParticipation.setRepositoryUri("http://localhost/git/abc/abc-solution.git");
        exercise.setSolutionParticipation(solutionParticipation);

        when(gitService.getOrCheckoutRepository(any(LocalVCRepositoryUri.class), eq(true), eq("main"), eq(false))).thenReturn(repository);
        when(gitService.getLastCommitHash(any(LocalVCRepositoryUri.class))).thenReturn(originalCommitId, newCommitId, newCommitId);
        when(gitService.getFileByName(repository, "Test.java")).thenReturn(Optional.empty());
        doNothing().when(repositoryService).createFile(eq(repository), eq("Test.java"), any());
        doNothing().when(repositoryService).commitChanges(repository, user);
        doNothing().when(gitService).resetToOriginHead(repository);
        when(repositoryStructureService.getRepositoryStructure(repository)).thenReturn("structure");
        when(solutionStrategy.generateCode(eq(user), eq(exercise), eq(1L), any(), any(), any())).thenReturn(List.of(new GeneratedFileDTO("Test.java", "public class Test {}")));
        when(programmingExerciseParticipationService.retrieveSolutionParticipation(exercise)).thenReturn(solutionParticipation);
        doNothing().when(continuousIntegrationTriggerService).triggerBuild(solutionParticipation, "new-hash", RepositoryType.SOLUTION);
        when(solutionProgrammingExerciseParticipationRepository.findByProgrammingExerciseId(exercise.getId())).thenReturn(Optional.of(solutionParticipation));

        ProgrammingSubmission submission = mock(ProgrammingSubmission.class);
        Result buildResult = mock(Result.class);
        when(programmingSubmissionRepository.findFirstByParticipationIdAndCommitHashOrderByIdDescWithFeedbacksAndTeamStudents(eq(99L), eq("new-hash"))).thenReturn(submission);
        when(resultRepository.findLatestResultWithFeedbacksAndTestcasesForSubmission(org.mockito.ArgumentMatchers.anyLong())).thenReturn(Optional.of(buildResult));
        when(buildResult.isSuccessful()).thenReturn(true);
        when(exerciseVersionService.isRepositoryTypeVersionable(RepositoryType.SOLUTION)).thenReturn(true);

        Result result = service.generateAndCompileCode(exercise, user, 1L, RepositoryType.SOLUTION, publisher);

        assertThat(result).isEqualTo(buildResult);
        verify(exerciseVersionService).createExerciseVersion(exercise, user);
        verify(publisher).done(HyperionCodeGenerationEventDTO.CompletionStatus.SUCCESS, HyperionCodeGenerationEventDTO.CompletionReason.BUILD_SUCCEEDED, Map.of(), 1,
                "Solution files were generated and committed to the solution repository.");
    }

    @Test
    void generateAndCompileCode_withNoGeneratedFiles_reportsErrorCompletion() throws Exception {
        HyperionCodeGenerationEventPublisher publisher = mock(HyperionCodeGenerationEventPublisher.class);
        Repository repository = mock(Repository.class);
        String originalCommitId = "orig-hash";
        SolutionProgrammingExerciseParticipation solutionParticipation = new SolutionProgrammingExerciseParticipation();
        solutionParticipation.setId(99L);
        solutionParticipation.setRepositoryUri("http://localhost/git/abc/abc-solution.git");
        exercise.setSolutionParticipation(solutionParticipation);

        when(gitService.getOrCheckoutRepository(any(LocalVCRepositoryUri.class), eq(true), eq("main"), eq(false))).thenReturn(repository);
        when(gitService.getLastCommitHash(any(LocalVCRepositoryUri.class))).thenReturn(originalCommitId);
        doNothing().when(gitService).resetToOriginHead(repository);
        when(repositoryStructureService.getRepositoryStructure(repository)).thenReturn("structure");
        when(solutionStrategy.generateCode(eq(user), eq(exercise), eq(1L), any(), any(), any())).thenReturn(List.of());
        when(exerciseVersionService.isRepositoryTypeVersionable(RepositoryType.SOLUTION)).thenReturn(false);

        Result result = service.generateAndCompileCode(exercise, user, 1L, RepositoryType.SOLUTION, publisher);

        assertThat(result).isNull();
        verify(continuousIntegrationTriggerService, never()).triggerBuild(any(), anyString(), any());
        verify(publisher).done(HyperionCodeGenerationEventDTO.CompletionStatus.ERROR, HyperionCodeGenerationEventDTO.CompletionReason.NO_COMMITTED_FILES, Map.of(), 1,
                "Solution generation did not produce any committed files.");
        verify(exerciseVersionService, never()).createExerciseVersion(any(), any());
    }

    @Test
    void generateAndCompileCode_withCommittedFilesAndFailedBuild_reportsPartialCompletion() throws Exception {
        HyperionCodeGenerationEventPublisher publisher = mock(HyperionCodeGenerationEventPublisher.class);
        Repository repository = mock(Repository.class);
        String originalCommitId = "orig-hash";
        String newCommitId = "new-hash";
        SolutionProgrammingExerciseParticipation solutionParticipation = new SolutionProgrammingExerciseParticipation();
        solutionParticipation.setId(99L);
        solutionParticipation.setRepositoryUri("http://localhost/git/abc/abc-solution.git");
        exercise.setSolutionParticipation(solutionParticipation);

        when(gitService.getOrCheckoutRepository(any(LocalVCRepositoryUri.class), eq(true), eq("main"), eq(false))).thenReturn(repository);
        when(gitService.getLastCommitHash(any(LocalVCRepositoryUri.class))).thenReturn(originalCommitId, newCommitId, newCommitId);
        when(gitService.getFileByName(repository, "Test.java")).thenReturn(Optional.empty());
        doNothing().when(repositoryService).createFile(eq(repository), eq("Test.java"), any());
        doNothing().when(repositoryService).commitChanges(repository, user);
        doNothing().when(gitService).resetToOriginHead(repository);
        when(repositoryStructureService.getRepositoryStructure(repository)).thenReturn("structure");
        when(solutionStrategy.generateCode(eq(user), eq(exercise), eq(1L), any(), any(), any())).thenReturn(List.of(new GeneratedFileDTO("Test.java", "public class Test {}")));
        when(programmingExerciseParticipationService.retrieveSolutionParticipation(exercise)).thenReturn(solutionParticipation);
        doNothing().when(continuousIntegrationTriggerService).triggerBuild(solutionParticipation, "new-hash", RepositoryType.SOLUTION);
        when(solutionProgrammingExerciseParticipationRepository.findByProgrammingExerciseId(exercise.getId())).thenReturn(Optional.of(solutionParticipation));

        ProgrammingSubmission submission = mock(ProgrammingSubmission.class);
        Result buildResult = mock(Result.class);
        when(programmingSubmissionRepository.findFirstByParticipationIdAndCommitHashOrderByIdDescWithFeedbacksAndTeamStudents(eq(99L), eq("new-hash"))).thenReturn(submission);
        when(resultRepository.findLatestResultWithFeedbacksAndTestcasesForSubmission(org.mockito.ArgumentMatchers.anyLong())).thenReturn(Optional.of(buildResult));
        when(buildResult.isSuccessful()).thenReturn(false);
        when(exerciseVersionService.isRepositoryTypeVersionable(RepositoryType.SOLUTION)).thenReturn(false);

        Result result = service.generateAndCompileCode(exercise, user, 1L, RepositoryType.SOLUTION, publisher);

        assertThat(result).isEqualTo(buildResult);
        verify(solutionStrategy, times(2)).generateCode(eq(user), eq(exercise), eq(1L), any(), any(), any());
        verify(publisher).done(HyperionCodeGenerationEventDTO.CompletionStatus.PARTIAL, HyperionCodeGenerationEventDTO.CompletionReason.BUILD_FAILED, Map.of(), 2,
                "Solution files were generated and committed to the solution repository, but the build failed.");
    }

    @Test
    void generateAndCompileCode_withCommittedFilesAndMissingBuildResult_reportsNoBuildResultMessage() throws Exception {
        HyperionCodeGenerationEventPublisher publisher = mock(HyperionCodeGenerationEventPublisher.class);
        Repository repository = mock(Repository.class);
        String originalCommitId = "orig-hash";
        String newCommitId = "new-hash";
        SolutionProgrammingExerciseParticipation solutionParticipation = new SolutionProgrammingExerciseParticipation();
        solutionParticipation.setId(99L);
        solutionParticipation.setRepositoryUri("http://localhost/git/abc/abc-solution.git");
        exercise.setSolutionParticipation(solutionParticipation);

        when(gitService.getOrCheckoutRepository(any(LocalVCRepositoryUri.class), eq(true), eq("main"), eq(false))).thenReturn(repository);
        when(gitService.getLastCommitHash(any(LocalVCRepositoryUri.class))).thenReturn(originalCommitId, newCommitId, newCommitId);
        when(gitService.getFileByName(repository, "Test.java")).thenReturn(Optional.empty());
        doNothing().when(repositoryService).createFile(eq(repository), eq("Test.java"), any());
        doNothing().when(repositoryService).commitChanges(repository, user);
        doNothing().when(gitService).resetToOriginHead(repository);
        when(repositoryStructureService.getRepositoryStructure(repository)).thenReturn("structure");
        when(solutionStrategy.generateCode(eq(user), eq(exercise), eq(1L), any(), any(), any())).thenReturn(List.of(new GeneratedFileDTO("Test.java", "public class Test {}")));
        when(programmingExerciseParticipationService.retrieveSolutionParticipation(exercise)).thenReturn(solutionParticipation);
        doNothing().when(continuousIntegrationTriggerService).triggerBuild(solutionParticipation, "new-hash", RepositoryType.SOLUTION);
        when(solutionProgrammingExerciseParticipationRepository.findByProgrammingExerciseId(exercise.getId())).thenReturn(Optional.empty());
        when(exerciseVersionService.isRepositoryTypeVersionable(RepositoryType.SOLUTION)).thenReturn(false);

        Result result = service.generateAndCompileCode(exercise, user, 1L, RepositoryType.SOLUTION, publisher);

        assertThat(result).isNull();
        verify(solutionStrategy, times(1)).generateCode(eq(user), eq(exercise), eq(1L), any(), any(), any());
        verify(publisher).done(HyperionCodeGenerationEventDTO.CompletionStatus.PARTIAL, HyperionCodeGenerationEventDTO.CompletionReason.PARTICIPATION_NOT_FOUND, Map.of(), 1,
                "Solution files were generated and committed to the solution repository, but Hyperion could not resolve the participation needed to read the build result.");
    }

    @Test
    void generateAndCompileCode_withInterruptedBuildPolling_preservesCommittedPartialState() throws Exception {
        HyperionCodeGenerationEventPublisher publisher = mock(HyperionCodeGenerationEventPublisher.class);
        Repository repository = mock(Repository.class);
        String originalCommitId = "orig-hash";
        String newCommitId = "new-hash";
        SolutionProgrammingExerciseParticipation solutionParticipation = new SolutionProgrammingExerciseParticipation();
        solutionParticipation.setId(99L);
        solutionParticipation.setRepositoryUri("http://localhost/git/abc/abc-solution.git");
        exercise.setSolutionParticipation(solutionParticipation);

        when(gitService.getOrCheckoutRepository(any(LocalVCRepositoryUri.class), eq(true), eq("main"), eq(false))).thenReturn(repository);
        when(gitService.getLastCommitHash(any(LocalVCRepositoryUri.class))).thenReturn(originalCommitId, newCommitId);
        when(gitService.getFileByName(repository, "Test.java")).thenReturn(Optional.empty());
        doNothing().when(repositoryService).createFile(eq(repository), eq("Test.java"), any());
        doNothing().when(repositoryService).commitChanges(repository, user);
        doNothing().when(gitService).resetToOriginHead(repository);
        when(repositoryStructureService.getRepositoryStructure(repository)).thenReturn("structure");
        when(solutionStrategy.generateCode(eq(user), eq(exercise), eq(1L), any(), any(), any())).thenReturn(List.of(new GeneratedFileDTO("Test.java", "public class Test {}")));
        when(programmingExerciseParticipationService.retrieveSolutionParticipation(exercise)).thenReturn(solutionParticipation);
        doNothing().when(continuousIntegrationTriggerService).triggerBuild(solutionParticipation, "new-hash", RepositoryType.SOLUTION);
        when(solutionProgrammingExerciseParticipationRepository.findByProgrammingExerciseId(exercise.getId())).thenReturn(Optional.of(solutionParticipation));
        when(programmingSubmissionRepository.findFirstByParticipationIdAndCommitHashOrderByIdDescWithFeedbacksAndTeamStudents(eq(99L), eq("new-hash"))).thenReturn(null);
        when(exerciseVersionService.isRepositoryTypeVersionable(RepositoryType.SOLUTION)).thenReturn(true);

        try {
            Thread.currentThread().interrupt();

            Result result = service.generateAndCompileCode(exercise, user, 1L, RepositoryType.SOLUTION, publisher);

            assertThat(result).isNull();
            verify(publisher).error("sleep interrupted");
            verify(publisher).done(HyperionCodeGenerationEventDTO.CompletionStatus.PARTIAL, HyperionCodeGenerationEventDTO.CompletionReason.BUILD_TIMED_OUT, Map.of(), 1,
                    "Solution files were generated and committed to the solution repository, but the build result is not available yet because polling timed out.");
            verify(exerciseVersionService).createExerciseVersion(exercise, user);
        }
        finally {
            Thread.interrupted();
        }
    }

    @Test
    void generateAndCompileCode_withCiTriggerFailure_skipsPollingAndReportsTriggerFailure() throws Exception {
        HyperionCodeGenerationEventPublisher publisher = mock(HyperionCodeGenerationEventPublisher.class);
        Repository repository = mock(Repository.class);
        String originalCommitId = "orig-hash";
        String newCommitId = "new-hash";
        SolutionProgrammingExerciseParticipation solutionParticipation = new SolutionProgrammingExerciseParticipation();
        solutionParticipation.setId(99L);
        solutionParticipation.setRepositoryUri("http://localhost/git/abc/abc-solution.git");
        exercise.setSolutionParticipation(solutionParticipation);

        when(gitService.getOrCheckoutRepository(any(LocalVCRepositoryUri.class), eq(true), eq("main"), eq(false))).thenReturn(repository);
        when(gitService.getLastCommitHash(any(LocalVCRepositoryUri.class))).thenReturn(originalCommitId, newCommitId, newCommitId);
        when(gitService.getFileByName(repository, "Test.java")).thenReturn(Optional.empty());
        doNothing().when(repositoryService).createFile(eq(repository), eq("Test.java"), any());
        doNothing().when(repositoryService).commitChanges(repository, user);
        doNothing().when(gitService).resetToOriginHead(repository);
        when(repositoryStructureService.getRepositoryStructure(repository)).thenReturn("structure");
        when(solutionStrategy.generateCode(eq(user), eq(exercise), eq(1L), any(), any(), any())).thenReturn(List.of(new GeneratedFileDTO("Test.java", "public class Test {}")));
        when(programmingExerciseParticipationService.retrieveSolutionParticipation(exercise)).thenReturn(solutionParticipation);
        doThrow(new ContinuousIntegrationException("CI error")).when(continuousIntegrationTriggerService).triggerBuild(solutionParticipation, "new-hash", RepositoryType.SOLUTION);
        when(exerciseVersionService.isRepositoryTypeVersionable(RepositoryType.SOLUTION)).thenReturn(true);

        Result result = service.generateAndCompileCode(exercise, user, 1L, RepositoryType.SOLUTION, publisher);

        assertThat(result).isNull();
        verify(programmingSubmissionRepository, never()).findFirstByParticipationIdAndCommitHashOrderByIdDescWithFeedbacksAndTeamStudents(anyLong(), anyString());
        verify(resultRepository, never()).findLatestResultWithFeedbacksAndTestcasesForSubmission(anyLong());
        verify(solutionStrategy, times(1)).generateCode(eq(user), eq(exercise), eq(1L), any(), any(), any());
        verify(publisher).done(HyperionCodeGenerationEventDTO.CompletionStatus.PARTIAL, HyperionCodeGenerationEventDTO.CompletionReason.CI_TRIGGER_FAILED, Map.of(), 1,
                "Solution files were generated and committed to the solution repository, but Hyperion could not trigger the CI build.");
        verify(exerciseVersionService).createExerciseVersion(exercise, user);
    }

    @Test
    void extractBuildLogs_withNullResult_returnsDefaultMessage() {
        String result = ReflectionTestUtils.invokeMethod(service, "extractBuildLogs", (Object) null);

        assertThat(result).isEqualTo("Build failed to produce a result.");
    }

    @Test
    void cleanupRepository_withValidInputs_callsGitService() {
        Repository mockRepository = mock(Repository.class);
        ReflectionTestUtils.invokeMethod(service, "cleanupRepository", mockRepository, "commit-hash");
        verify(gitService, times(1)).resetToOriginHead(mockRepository);
    }

    @Test
    void cleanupRepository_withNullRepository_doesNotThrow() {
        ReflectionTestUtils.invokeMethod(service, "cleanupRepository", null, "commit-hash");
    }

    @Test
    void cleanupRepository_withNullCommitHash_doesNotThrow() {
        Repository mockRepository = mock(Repository.class);
        ReflectionTestUtils.invokeMethod(service, "cleanupRepository", mockRepository, null);
    }

    @Test
    void setupRepository_withNullRepositoryUri_returnsFalse() {
        // Don't set any repository URI, so getRepositoryURI will return null

        Object result = ReflectionTestUtils.invokeMethod(service, "setupRepository", exercise, RepositoryType.SOLUTION);

        assertThat(result).hasFieldOrPropertyWithValue("success", false);
    }

    @Test
    void buildConsistencyIssuesPrompt_withNoIssues_returnsNone() {
        when(consistencyCheckService.checkConsistency(exercise.getId())).thenReturn(new ConsistencyCheckResponseDTO(Instant.EPOCH, List.of(), null, null, null));

        String result = ReflectionTestUtils.invokeMethod(service, "buildConsistencyIssuesPrompt", exercise);

        assertThat(result).isEqualTo("None");
    }

    @Test
    void buildConsistencyIssuesPrompt_withLocations_formatsAndDefaultsMissingPath() {
        ArtifactLocationDTO problemStatementLocation = new ArtifactLocationDTO(ArtifactType.PROBLEM_STATEMENT, "", 1, 2);
        ArtifactLocationDTO templateLocation = new ArtifactLocationDTO(ArtifactType.TEMPLATE_REPOSITORY, "src/Main.java", 5, 7);
        ConsistencyIssueDTO issue = new ConsistencyIssueDTO(Severity.HIGH, ConsistencyIssueCategory.METHOD_RETURN_TYPE_MISMATCH, "desc", "fix",
                List.of(problemStatementLocation, templateLocation));

        when(consistencyCheckService.checkConsistency(exercise.getId())).thenReturn(new ConsistencyCheckResponseDTO(Instant.EPOCH, List.of(issue), null, null, null));

        String result = ReflectionTestUtils.invokeMethod(service, "buildConsistencyIssuesPrompt", exercise);

        assertThat(result).isEqualTo(
                "1. [HIGH] METHOD_RETURN_TYPE_MISMATCH: desc\n   Suggested fix: fix\n   Locations: PROBLEM_STATEMENT:problem_statement.md:1-2; TEMPLATE_REPOSITORY:src/Main.java:5-7");
    }

    @Test
    void buildConsistencyIssuesPrompt_withRuntimeException_returnsUnavailableMarker() {
        when(consistencyCheckService.checkConsistency(exercise.getId())).thenThrow(new RuntimeException("consistency service unavailable"));

        String result = ReflectionTestUtils.invokeMethod(service, "buildConsistencyIssuesPrompt", exercise);

        assertThat(result).isEqualTo("Unavailable (consistency check failed)");
    }

    @Test
    void updateSingleFile_withExistingFile_deletesAndCreatesFile() throws Exception {
        Repository mockRepository = mock(Repository.class);
        GeneratedFileDTO generatedFile = new GeneratedFileDTO("Test.java", "public class Test {}");
        File existingFile = mock(File.class);

        when(gitService.getFileByName(mockRepository, "Test.java")).thenReturn(Optional.of(existingFile));
        doNothing().when(repositoryService).deleteFile(mockRepository, "Test.java");
        doNothing().when(repositoryService).createFile(eq(mockRepository), eq("Test.java"), any());

        ReflectionTestUtils.invokeMethod(service, "updateSingleFile", mockRepository, generatedFile, exercise);

        verify(repositoryService).deleteFile(mockRepository, "Test.java");
        verify(repositoryService).createFile(eq(mockRepository), eq("Test.java"), any());
    }

    @Test
    void updateSingleFile_withNonExistingFile_createsFileOnly() throws Exception {
        Repository mockRepository = mock(Repository.class);
        GeneratedFileDTO generatedFile = new GeneratedFileDTO("NewTest.java", "public class NewTest {}");

        when(gitService.getFileByName(mockRepository, "NewTest.java")).thenReturn(Optional.empty());
        doNothing().when(repositoryService).createFile(eq(mockRepository), eq("NewTest.java"), any());

        ReflectionTestUtils.invokeMethod(service, "updateSingleFile", mockRepository, generatedFile, exercise);

        verify(repositoryService, never()).deleteFile(any(), anyString());
        verify(repositoryService).createFile(eq(mockRepository), eq("NewTest.java"), any());
    }

    @Test
    void updateSingleFile_withIOException_throwsException() throws Exception {
        Repository mockRepository = mock(Repository.class);
        GeneratedFileDTO generatedFile = new GeneratedFileDTO("Test.java", "public class Test {}");

        when(gitService.getFileByName(mockRepository, "Test.java")).thenReturn(Optional.empty());
        doThrow(new IOException("IO error")).when(repositoryService).createFile(eq(mockRepository), eq("Test.java"), any());

        // When using reflection, IOException gets wrapped in UndeclaredThrowableException
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service, "updateSingleFile", mockRepository, generatedFile, exercise)).isInstanceOf(Exception.class)
                .hasRootCauseInstanceOf(IOException.class);
    }

    @Test
    void commitAndGetHash_withSuccessfulCommit_returnsHash() throws Exception {
        Repository mockRepository = mock(Repository.class);
        LocalVCRepositoryUri repositoryUri = mock(LocalVCRepositoryUri.class);
        SolutionProgrammingExerciseParticipation mockParticipation = mock(SolutionProgrammingExerciseParticipation.class);

        doNothing().when(repositoryService).commitChanges(mockRepository, user);
        when(gitService.getLastCommitHash(repositoryUri)).thenReturn("new-commit-hash");
        when(programmingExerciseParticipationService.retrieveSolutionParticipation(exercise)).thenReturn(mockParticipation);
        doNothing().when(continuousIntegrationTriggerService).triggerBuild(mockParticipation, "new-commit-hash", RepositoryType.SOLUTION);

        Object result = ReflectionTestUtils.invokeMethod(service, "commitAndGetHash", mockRepository, user, repositoryUri, exercise, RepositoryType.SOLUTION);

        assertThat(ReflectionTestUtils.getField(result, "commitHash")).isEqualTo("new-commit-hash");
        assertThat(ReflectionTestUtils.getField(result, "buildTriggered")).isEqualTo(true);
        verify(continuousIntegrationTriggerService).triggerBuild(mockParticipation, "new-commit-hash", RepositoryType.SOLUTION);
    }

    @Test
    void commitAndGetHash_withCIException_returnsHashAndFailedTriggerState() throws Exception {
        Repository mockRepository = mock(Repository.class);
        LocalVCRepositoryUri repositoryUri = mock(LocalVCRepositoryUri.class);
        SolutionProgrammingExerciseParticipation mockParticipation = mock(SolutionProgrammingExerciseParticipation.class);

        doNothing().when(repositoryService).commitChanges(mockRepository, user);
        when(gitService.getLastCommitHash(repositoryUri)).thenReturn("new-commit-hash");
        when(programmingExerciseParticipationService.retrieveSolutionParticipation(exercise)).thenReturn(mockParticipation);
        doThrow(new ContinuousIntegrationException("CI error")).when(continuousIntegrationTriggerService).triggerBuild(mockParticipation, "new-commit-hash",
                RepositoryType.SOLUTION);

        Object result = ReflectionTestUtils.invokeMethod(service, "commitAndGetHash", mockRepository, user, repositoryUri, exercise, RepositoryType.SOLUTION);

        assertThat(ReflectionTestUtils.getField(result, "commitHash")).isEqualTo("new-commit-hash");
        assertThat(ReflectionTestUtils.getField(result, "buildTriggered")).isEqualTo(false);
    }

    @Test
    void commitAndGetHash_withTestsRepositoryType_createsTestSubmissionAndTriggersCI() throws Exception {
        Repository mockRepository = mock(Repository.class);
        LocalVCRepositoryUri repositoryUri = mock(LocalVCRepositoryUri.class);
        SolutionProgrammingExerciseParticipation mockParticipation = mock(SolutionProgrammingExerciseParticipation.class);

        doNothing().when(repositoryService).commitChanges(mockRepository, user);
        when(gitService.getLastCommitHash(repositoryUri)).thenReturn("commit-tests");
        when(programmingExerciseParticipationService.retrieveSolutionParticipation(exercise)).thenReturn(mockParticipation);

        Object result = ReflectionTestUtils.invokeMethod(service, "commitAndGetHash", mockRepository, user, repositoryUri, exercise, RepositoryType.TESTS);

        assertThat(ReflectionTestUtils.getField(result, "commitHash")).isEqualTo("commit-tests");
        assertThat(ReflectionTestUtils.getField(result, "buildTriggered")).isEqualTo(true);
        verify(programmingSubmissionService, times(1)).createSolutionParticipationSubmissionWithTypeTest(exercise.getId(), "commit-tests");
        verify(continuousIntegrationTriggerService, times(1)).triggerBuild(mockParticipation, "commit-tests", RepositoryType.TESTS);
    }

    @Test
    void extractBuildLogs_withProgrammingSubmission_returnsConcatenatedLogs() {
        Result mockResult = mock(Result.class);
        ProgrammingSubmission mockSubmission = mock(ProgrammingSubmission.class);
        BuildLogEntry logEntry1 = mock(BuildLogEntry.class);
        BuildLogEntry logEntry2 = mock(BuildLogEntry.class);
        List<BuildLogEntry> logEntries = List.of(logEntry1, logEntry2);

        when(mockResult.getSubmission()).thenReturn(mockSubmission);
        when(mockSubmission.getBuildLogEntries()).thenReturn(logEntries);
        when(logEntry1.getLog()).thenReturn("Error in line 1");
        when(logEntry2.getLog()).thenReturn("Error in line 2");

        String result = ReflectionTestUtils.invokeMethod(service, "extractBuildLogs", mockResult);

        assertThat(result).isEqualTo("Error in line 1\nError in line 2");
    }

    @Test
    void extractBuildLogs_withNonProgrammingSubmission_returnsDefaultMessage() {
        Result mockResult = mock(Result.class);

        when(mockResult.getSubmission()).thenReturn(null);

        String result = ReflectionTestUtils.invokeMethod(service, "extractBuildLogs", mockResult);

        assertThat(result).isEqualTo("Build failed to produce a result.");
    }

    @Test
    void waitForBuildResult_withNoParticipation_returnsParticipationNotFoundOutcome() {
        when(solutionProgrammingExerciseParticipationRepository.findByProgrammingExerciseId(exercise.getId())).thenReturn(Optional.empty());
        when(templateProgrammingExerciseParticipationRepository.findByProgrammingExerciseId(exercise.getId())).thenReturn(Optional.empty());

        Object resultSolution = ReflectionTestUtils.invokeMethod(service, "waitForBuildResult", exercise, "commit-hash", RepositoryType.SOLUTION);
        Object resultTemplate = ReflectionTestUtils.invokeMethod(service, "waitForBuildResult", exercise, "commit-hash", RepositoryType.TEMPLATE);

        assertThat(resultSolution).hasFieldOrPropertyWithValue("result", null);
        assertThat(ReflectionTestUtils.getField(resultSolution, "state")).hasToString("PARTICIPATION_NOT_FOUND");
        assertThat(resultTemplate).hasFieldOrPropertyWithValue("result", null);
        assertThat(ReflectionTestUtils.getField(resultTemplate, "state")).hasToString("PARTICIPATION_NOT_FOUND");
    }

    @Test
    void waitForBuildResult_withSuccessfulResult_returnsSuccessOutcome() {
        SolutionProgrammingExerciseParticipation solutionParticipation = new SolutionProgrammingExerciseParticipation();
        solutionParticipation.setId(99L);
        ProgrammingSubmission submission = mock(ProgrammingSubmission.class);
        Result buildResult = mock(Result.class);

        when(solutionProgrammingExerciseParticipationRepository.findByProgrammingExerciseId(exercise.getId())).thenReturn(Optional.of(solutionParticipation));
        when(templateProgrammingExerciseParticipationRepository.findByProgrammingExerciseId(exercise.getId())).thenReturn(Optional.empty());
        when(programmingSubmissionRepository.findFirstByParticipationIdAndCommitHashOrderByIdDescWithFeedbacksAndTeamStudents(99L, "commit-hash")).thenReturn(submission);
        when(resultRepository.findLatestResultWithFeedbacksAndTestcasesForSubmission(submission.getId())).thenReturn(Optional.of(buildResult));
        when(buildResult.isSuccessful()).thenReturn(true);

        Object outcome = ReflectionTestUtils.invokeMethod(service, "waitForBuildResult", exercise, "commit-hash", RepositoryType.SOLUTION);

        assertThat(ReflectionTestUtils.getField(outcome, "result")).isEqualTo(buildResult);
        assertThat(ReflectionTestUtils.getField(outcome, "state")).hasToString("SUCCESS");
    }

    @Test
    void waitForBuildResult_withFailedResult_returnsFailedOutcome() {
        SolutionProgrammingExerciseParticipation solutionParticipation = new SolutionProgrammingExerciseParticipation();
        solutionParticipation.setId(100L);
        ProgrammingSubmission submission = mock(ProgrammingSubmission.class);
        Result buildResult = mock(Result.class);

        when(solutionProgrammingExerciseParticipationRepository.findByProgrammingExerciseId(exercise.getId())).thenReturn(Optional.of(solutionParticipation));
        when(templateProgrammingExerciseParticipationRepository.findByProgrammingExerciseId(exercise.getId())).thenReturn(Optional.empty());
        when(programmingSubmissionRepository.findFirstByParticipationIdAndCommitHashOrderByIdDescWithFeedbacksAndTeamStudents(100L, "commit-hash")).thenReturn(submission);
        when(resultRepository.findLatestResultWithFeedbacksAndTestcasesForSubmission(submission.getId())).thenReturn(Optional.of(buildResult));
        when(buildResult.isSuccessful()).thenReturn(false);

        Object outcome = ReflectionTestUtils.invokeMethod(service, "waitForBuildResult", exercise, "commit-hash", RepositoryType.SOLUTION);

        assertThat(ReflectionTestUtils.getField(outcome, "result")).isEqualTo(buildResult);
        assertThat(ReflectionTestUtils.getField(outcome, "state")).hasToString("FAILED");
    }
}
