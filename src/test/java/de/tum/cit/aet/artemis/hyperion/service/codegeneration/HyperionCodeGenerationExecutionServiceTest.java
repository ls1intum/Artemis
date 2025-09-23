package de.tum.cit.aet.artemis.hyperion.service.codegeneration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.context.ApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.hyperion.service.HyperionProgrammingExerciseContextRendererService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingSubmissionRepository;
import de.tum.cit.aet.artemis.programming.repository.SolutionProgrammingExerciseParticipationRepository;
import de.tum.cit.aet.artemis.programming.service.GitService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseParticipationService;
import de.tum.cit.aet.artemis.programming.service.RepositoryService;
import de.tum.cit.aet.artemis.programming.service.ci.ContinuousIntegrationTriggerService;

class HyperionCodeGenerationExecutionServiceTest {

    @Mock
    private GitService gitService;

    @Mock
    private ApplicationContext applicationContext;

    @Mock
    private RepositoryService repositoryService;

    @Mock
    private SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository;

    @Mock
    private ProgrammingSubmissionRepository programmingSubmissionRepository;

    @Mock
    private ResultRepository resultRepository;

    @Mock
    private ContinuousIntegrationTriggerService continuousIntegrationTriggerService;

    @Mock
    private ProgrammingExerciseParticipationService programmingExerciseParticipationService;

    @Mock
    private HyperionProgrammingExerciseContextRendererService repositoryStructureService;

    @Mock
    private HyperionCodeGenerationStrategy mockStrategy;

    private HyperionCodeGenerationExecutionService service;

    private User user;

    private ProgrammingExercise exercise;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        this.service = new HyperionCodeGenerationExecutionService(gitService, applicationContext, repositoryService, solutionProgrammingExerciseParticipationRepository,
                programmingSubmissionRepository, resultRepository, continuousIntegrationTriggerService, programmingExerciseParticipationService, repositoryStructureService);

        this.user = new User();
        user.setLogin("testuser");

        this.exercise = new ProgrammingExercise();
        exercise.setId(1L);
        exercise.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        exercise.setProblemStatement("Implement sorting algorithm");
    }

    @Test
    void resolveStrategy_withSolutionRepositoryType_returnsSolutionStrategy() throws Exception {
        when(applicationContext.getBean("solutionRepositoryStrategy", HyperionCodeGenerationStrategy.class)).thenReturn(mockStrategy);

        HyperionCodeGenerationStrategy result = (HyperionCodeGenerationStrategy) ReflectionTestUtils.invokeMethod(service, "resolveStrategy", RepositoryType.SOLUTION);

        assertThat(result).isEqualTo(mockStrategy);
    }

    @Test
    void resolveStrategy_withTemplateRepositoryType_returnsTemplateStrategy() throws Exception {
        when(applicationContext.getBean("templateRepositoryStrategy", HyperionCodeGenerationStrategy.class)).thenReturn(mockStrategy);

        HyperionCodeGenerationStrategy result = (HyperionCodeGenerationStrategy) ReflectionTestUtils.invokeMethod(service, "resolveStrategy", RepositoryType.TEMPLATE);

        assertThat(result).isEqualTo(mockStrategy);
    }

    @Test
    void resolveStrategy_withTestsRepositoryType_returnsTestStrategy() throws Exception {
        when(applicationContext.getBean("testRepositoryStrategy", HyperionCodeGenerationStrategy.class)).thenReturn(mockStrategy);

        HyperionCodeGenerationStrategy result = (HyperionCodeGenerationStrategy) ReflectionTestUtils.invokeMethod(service, "resolveStrategy", RepositoryType.TESTS);

        assertThat(result).isEqualTo(mockStrategy);
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
        Result result = service.generateAndCompileCode(exercise, user, RepositoryType.SOLUTION);

        assertThat(result).isNull();
    }

    @Test
    void extractBuildLogs_withNullResult_returnsDefaultMessage() {
        String result = (String) ReflectionTestUtils.invokeMethod(service, "extractBuildLogs", (Object) null);

        assertThat(result).isEqualTo("Build failed to produce a result.");
    }

    @Test
    void cleanupRepository_withValidInputs_callsGitService() throws Exception {
        ReflectionTestUtils.invokeMethod(service, "cleanupRepository", gitService.getOrCheckoutRepository(null, false, "main", false), "commit-hash");
    }

    @Test
    void cleanupRepository_withNullRepository_doesNotThrow() throws Exception {
        ReflectionTestUtils.invokeMethod(service, "cleanupRepository", (Object) null, "commit-hash");
    }

    @Test
    void cleanupRepository_withNullCommitHash_doesNotThrow() throws Exception {
        ReflectionTestUtils.invokeMethod(service, "cleanupRepository", gitService.getOrCheckoutRepository(null, false, "main", false), (Object) null);
    }
}
