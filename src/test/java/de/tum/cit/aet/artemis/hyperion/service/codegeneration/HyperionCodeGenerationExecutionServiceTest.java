package de.tum.cit.aet.artemis.hyperion.service.codegeneration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
import java.util.List;
import java.util.Optional;

import org.eclipse.jgit.lib.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.test_repository.ResultTestRepository;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.ContinuousIntegrationException;
import de.tum.cit.aet.artemis.hyperion.dto.GeneratedFileDTO;
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
import de.tum.cit.aet.artemis.programming.repository.TemplateProgrammingExerciseParticipationRepository;
import de.tum.cit.aet.artemis.programming.service.GitService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseParticipationService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingSubmissionService;
import de.tum.cit.aet.artemis.programming.service.RepositoryService;
import de.tum.cit.aet.artemis.programming.service.ci.ContinuousIntegrationTriggerService;
import de.tum.cit.aet.artemis.programming.service.localvc.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingSubmissionTestRepository;

class HyperionCodeGenerationExecutionServiceTest {

    @Mock
    private GitService gitService;

    @Mock
    private RepositoryService repositoryService;

    @Mock
    private SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository;

    @Mock
    private TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository;

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
    private HyperionCodeGenerationService mockStrategy;

    @Mock
    private ProgrammingSubmissionService programmingSubmissionService;

    private HyperionCodeGenerationExecutionService service;

    private User user;

    private ProgrammingExercise exercise;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        this.service = new HyperionCodeGenerationExecutionService(gitService, repositoryService, solutionProgrammingExerciseParticipationRepository,
                templateProgrammingExerciseParticipationRepository, programmingSubmissionRepository, resultRepository, continuousIntegrationTriggerService,
                programmingExerciseParticipationService, repositoryStructureService, solutionStrategy, templateStrategy, testStrategy, programmingSubmissionService);

        this.user = new User();
        user.setLogin("testuser");

        this.exercise = new ProgrammingExercise();
        exercise.setId(1L);
        exercise.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        exercise.setProblemStatement("Implement sorting algorithm");
    }

    @Test
    void resolveStrategy_withSolutionRepositoryType_returnsSolutionStrategy() throws Exception {
        HyperionCodeGenerationService result = (HyperionCodeGenerationService) ReflectionTestUtils.invokeMethod(service, "resolveStrategy", RepositoryType.SOLUTION);

        assertThat(result).isEqualTo(solutionStrategy);
    }

    @Test
    void resolveStrategy_withTemplateRepositoryType_returnsTemplateStrategy() throws Exception {
        HyperionCodeGenerationService result = (HyperionCodeGenerationService) ReflectionTestUtils.invokeMethod(service, "resolveStrategy", RepositoryType.TEMPLATE);

        assertThat(result).isEqualTo(templateStrategy);
    }

    @Test
    void resolveStrategy_withTestsRepositoryType_returnsTestStrategy() throws Exception {
        HyperionCodeGenerationService result = (HyperionCodeGenerationService) ReflectionTestUtils.invokeMethod(service, "resolveStrategy", RepositoryType.TESTS);

        assertThat(result).isEqualTo(testStrategy);
    }

    @Test
    void resolveStrategy_withUnsupportedRepositoryType_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service, "resolveStrategy", RepositoryType.AUXILIARY)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported repository type for code generation: auxiliary");
    }

    @Test
    void getDefaultBranch_returnsGitServiceDefaultBranch() throws Exception {
        when(gitService.getDefaultBranch()).thenReturn("develop");

        String result = (String) ReflectionTestUtils.invokeMethod(service, "getDefaultBranch", (Object) null);

        assertThat(result).isEqualTo("develop");
    }

    @Test
    void generateAndCompileCode_withNullRepositoryUri_returnsNull() {
        HyperionCodeGenerationEventPublisher publisher = mock(HyperionCodeGenerationEventPublisher.class);

        Result result = service.generateAndCompileCode(exercise, user, RepositoryType.SOLUTION, publisher);

        assertThat(result).isNull();
        verify(publisher, times(1)).error(anyString());
    }

    @Test
    void extractBuildLogs_withNullResult_returnsDefaultMessage() {
        String result = (String) ReflectionTestUtils.invokeMethod(service, "extractBuildLogs", (Object) null);

        assertThat(result).isEqualTo("Build failed to produce a result.");
    }

    @Test
    void cleanupRepository_withValidInputs_callsGitService() throws Exception {
        Repository mockRepository = mock(Repository.class);
        ReflectionTestUtils.invokeMethod(service, "cleanupRepository", mockRepository, "commit-hash");
        verify(gitService, times(1)).resetToOriginHead(mockRepository);
    }

    @Test
    void cleanupRepository_withNullRepository_doesNotThrow() throws Exception {
        ReflectionTestUtils.invokeMethod(service, "cleanupRepository", (Object) null, "commit-hash");
    }

    @Test
    void cleanupRepository_withNullCommitHash_doesNotThrow() throws Exception {
        ReflectionTestUtils.invokeMethod(service, "cleanupRepository", gitService.getOrCheckoutRepository(null, false, "main", false), (Object) null);
    }

    @Test
    void setupRepository_withNullRepositoryUri_returnsFalse() throws Exception {
        // Don't set any repository URI, so getRepositoryURI will return null

        Object result = ReflectionTestUtils.invokeMethod(service, "setupRepository", exercise, RepositoryType.SOLUTION);

        assertThat(result).hasFieldOrPropertyWithValue("success", false);
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
    void processGeneratedFiles_withMultipleFiles_processesAll() throws Exception {
        Repository mockRepository = mock(Repository.class);
        List<GeneratedFileDTO> generatedFiles = List.of(new GeneratedFileDTO("Test1.java", "public class Test1 {}"), new GeneratedFileDTO("Test2.java", "public class Test2 {}"));

        when(gitService.getFileByName(mockRepository, "Test1.java")).thenReturn(Optional.empty());
        when(gitService.getFileByName(mockRepository, "Test2.java")).thenReturn(Optional.empty());
        doNothing().when(repositoryService).createFile(eq(mockRepository), anyString(), any());

        ReflectionTestUtils.invokeMethod(service, "processGeneratedFiles", mockRepository, generatedFiles, exercise);

        verify(repositoryService, times(2)).createFile(eq(mockRepository), anyString(), any());
    }

    @Test
    void commitAndGetHash_withSuccessfulCommit_returnsHash() throws Exception {
        Repository mockRepository = mock(Repository.class);
        LocalVCRepositoryUri repositoryUri = mock(LocalVCRepositoryUri.class);
        ObjectId mockCommitId = mock(ObjectId.class);
        SolutionProgrammingExerciseParticipation mockParticipation = mock(SolutionProgrammingExerciseParticipation.class);

        doNothing().when(repositoryService).commitChanges(mockRepository, user);
        when(gitService.getLastCommitHash(repositoryUri)).thenReturn(mockCommitId);
        when(mockCommitId.getName()).thenReturn("new-commit-hash");
        when(programmingExerciseParticipationService.retrieveSolutionParticipation(exercise)).thenReturn(mockParticipation);
        doNothing().when(continuousIntegrationTriggerService).triggerBuild(mockParticipation, "new-commit-hash", RepositoryType.SOLUTION);

        String result = (String) ReflectionTestUtils.invokeMethod(service, "commitAndGetHash", mockRepository, user, repositoryUri, exercise, RepositoryType.SOLUTION);

        assertThat(result).isEqualTo("new-commit-hash");
        verify(continuousIntegrationTriggerService).triggerBuild(mockParticipation, "new-commit-hash", RepositoryType.SOLUTION);
    }

    @Test
    void commitAndGetHash_withCIException_stillReturnsHash() throws Exception {
        Repository mockRepository = mock(Repository.class);
        LocalVCRepositoryUri repositoryUri = mock(LocalVCRepositoryUri.class);
        ObjectId mockCommitId = mock(ObjectId.class);
        SolutionProgrammingExerciseParticipation mockParticipation = mock(SolutionProgrammingExerciseParticipation.class);

        doNothing().when(repositoryService).commitChanges(mockRepository, user);
        when(gitService.getLastCommitHash(repositoryUri)).thenReturn(mockCommitId);
        when(mockCommitId.getName()).thenReturn("new-commit-hash");
        when(programmingExerciseParticipationService.retrieveSolutionParticipation(exercise)).thenReturn(mockParticipation);
        doThrow(new ContinuousIntegrationException("CI error")).when(continuousIntegrationTriggerService).triggerBuild(mockParticipation, "new-commit-hash",
                RepositoryType.SOLUTION);

        String result = (String) ReflectionTestUtils.invokeMethod(service, "commitAndGetHash", mockRepository, user, repositoryUri, exercise, RepositoryType.SOLUTION);

        assertThat(result).isEqualTo("new-commit-hash");
    }

    @Test
    void commitAndGetHash_withTestsRepositoryType_createsTestSubmissionAndTriggersCI() throws Exception {
        Repository mockRepository = mock(Repository.class);
        LocalVCRepositoryUri repositoryUri = mock(LocalVCRepositoryUri.class);
        ObjectId mockCommitId = mock(ObjectId.class);
        SolutionProgrammingExerciseParticipation mockParticipation = mock(SolutionProgrammingExerciseParticipation.class);

        doNothing().when(repositoryService).commitChanges(mockRepository, user);
        when(gitService.getLastCommitHash(repositoryUri)).thenReturn(mockCommitId);
        when(mockCommitId.getName()).thenReturn("commit-tests");
        when(programmingExerciseParticipationService.retrieveSolutionParticipation(exercise)).thenReturn(mockParticipation);

        String result = (String) ReflectionTestUtils.invokeMethod(service, "commitAndGetHash", mockRepository, user, repositoryUri, exercise, RepositoryType.TESTS);

        assertThat(result).isEqualTo("commit-tests");
        verify(programmingSubmissionService, times(1)).createSolutionParticipationSubmissionWithTypeTest(exercise.getId(), "commit-tests");
        verify(continuousIntegrationTriggerService, times(1)).triggerBuild(mockParticipation, "commit-tests", RepositoryType.TESTS);
    }

    @Test
    void extractBuildLogs_withProgrammingSubmission_returnsConcatenatedLogs() throws Exception {
        Result mockResult = mock(Result.class);
        ProgrammingSubmission mockSubmission = mock(ProgrammingSubmission.class);
        BuildLogEntry logEntry1 = mock(BuildLogEntry.class);
        BuildLogEntry logEntry2 = mock(BuildLogEntry.class);
        List<BuildLogEntry> logEntries = List.of(logEntry1, logEntry2);

        when(mockResult.getSubmission()).thenReturn(mockSubmission);
        when(mockSubmission.getBuildLogEntries()).thenReturn(logEntries);
        when(logEntry1.getLog()).thenReturn("Error in line 1");
        when(logEntry2.getLog()).thenReturn("Error in line 2");

        String result = (String) ReflectionTestUtils.invokeMethod(service, "extractBuildLogs", mockResult);

        assertThat(result).isEqualTo("Error in line 1\nError in line 2");
    }

    @Test
    void extractBuildLogs_withNonProgrammingSubmission_returnsDefaultMessage() throws Exception {
        Result mockResult = mock(Result.class);

        when(mockResult.getSubmission()).thenReturn(null);

        String result = (String) ReflectionTestUtils.invokeMethod(service, "extractBuildLogs", mockResult);

        assertThat(result).isEqualTo("Build failed to produce a result.");
    }

    @Test
    void waitForBuildResult_withNoParticipation_returnsNull() throws Exception {
        when(solutionProgrammingExerciseParticipationRepository.findByProgrammingExerciseId(exercise.getId())).thenReturn(Optional.empty());
        when(templateProgrammingExerciseParticipationRepository.findByProgrammingExerciseId(exercise.getId())).thenReturn(Optional.empty());

        Result resultSolution = (Result) ReflectionTestUtils.invokeMethod(service, "waitForBuildResult", exercise, "commit-hash", RepositoryType.SOLUTION);
        Result resultTemplate = (Result) ReflectionTestUtils.invokeMethod(service, "waitForBuildResult", exercise, "commit-hash", RepositoryType.TEMPLATE);

        assertThat(resultSolution).isNull();
        assertThat(resultTemplate).isNull();
    }
}
