package de.tum.cit.aet.artemis.programming.service.hades;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tum.cit.aet.artemis.localci.service.BuildPhaseEvaluationService;
import de.tum.cit.aet.artemis.localci.service.BuildPhasesTemplateService;
import de.tum.cit.aet.artemis.localci.service.BuildScriptProviderService;
import de.tum.cit.aet.artemis.localci.service.LocalCIBuildConfigurationService;
import de.tum.cit.aet.artemis.localvc.service.GitService;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.build.BuildPhaseCondition;
import de.tum.cit.aet.artemis.programming.dto.BuildPhaseDTO;
import de.tum.cit.aet.artemis.programming.exception.ContinuousIntegrationException;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.programming.service.hades.dto.BuildTriggerRequestDTO;

@ExtendWith(MockitoExtension.class)
class HadesTriggerServiceTest {

    @Mock
    private HadesService hadesService;

    @Mock
    private BuildPhaseEvaluationService buildPhaseEvaluationService;

    @Mock
    private BuildPhasesTemplateService buildPhasesTemplateService;

    @Mock
    private ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository;

    @Mock
    private GitService gitService;

    @Mock
    private LocalCIBuildConfigurationService localCIBuildConfigurationService;

    @Mock
    private BuildScriptProviderService buildScriptProviderService;

    @InjectMocks
    private HadesTriggerService hadesTriggerService;

    @Nested
    class TriggerBuildTests {

        @Mock
        private ProgrammingExerciseParticipation participation;

        @Mock
        private LocalVCRepositoryUri exerciseRepoUri;

        private ProgrammingExercise exercise;

        private ProgrammingExerciseBuildConfig buildConfig;

        @BeforeEach
        void setUp() throws Exception {
            exercise = new ProgrammingExercise();
            exercise.setId(10L);
            exercise.setProgrammingLanguage(ProgrammingLanguage.JAVA);
            exercise.setTestRepositoryUri("http://localhost/git/PROJ/proj-tests.git");

            buildConfig = new ProgrammingExerciseBuildConfig();
            buildConfig.setBuildPlanConfiguration(null);

            when(participation.getId()).thenReturn(2L);
            when(participation.getProgrammingExercise()).thenReturn(exercise);
            when(participation.getVcsRepositoryUri()).thenReturn(exerciseRepoUri);
            when(exerciseRepoUri.getURI()).thenReturn(new URI("http://localhost/git/PROJ/proj-exercise.git"));
            when(programmingExerciseBuildConfigRepository.getProgrammingExerciseBuildConfigElseThrow(exercise)).thenReturn(buildConfig);

            var phase = new BuildPhaseDTO("compile", "mvn compile", BuildPhaseCondition.ALWAYS, false, null);
            when(buildPhasesTemplateService.getDefaultBuildPlanPhasesFor(exercise)).thenReturn(List.of(phase));
            when(buildPhaseEvaluationService.determineActiveBuildPhases(any(), any())).thenReturn(List.of(phase));
        }

        @Test
        void triggerBuild_noArgs_delegatesToThreeArgOverload() throws ContinuousIntegrationException {
            when(gitService.getLastCommitHash(any(LocalVCRepositoryUri.class))).thenReturn("some-hash");

            hadesTriggerService.triggerBuild(participation);

            verify(hadesService).build(any());
        }

        @Test
        void triggerBuild_withTriggerAllFlag_ignoresFlagAndTriggers() throws ContinuousIntegrationException {
            when(gitService.getLastCommitHash(any(LocalVCRepositoryUri.class))).thenReturn("some-hash");

            hadesTriggerService.triggerBuild(participation, true);

            verify(hadesService).build(any());
        }

        @Test
        void triggerBuild_withCommitHashAndNullTriggeredBy_usesHashForAssignment() throws ContinuousIntegrationException {
            // when triggeredByPushTo=null and commitHash is set, assignment hash comes from commitHash directly (no git call)
            when(gitService.getLastCommitHash(any(LocalVCRepositoryUri.class))).thenReturn("test-hash");

            hadesTriggerService.triggerBuild(participation, "commit-abc", null);

            verify(hadesService).build(any());
            verify(gitService, never()).getLastCommitHash(exerciseRepoUri);
        }

        @Test
        void triggerBuild_withTestsRepositoryTrigger_usesCommitHashForTest() throws ContinuousIntegrationException {
            // when triggeredByPushTo=TESTS and commitHash is set, test hash comes from commitHash directly
            when(gitService.getLastCommitHash(exerciseRepoUri)).thenReturn("assign-hash");

            hadesTriggerService.triggerBuild(participation, "test-commit-abc", RepositoryType.TESTS);

            verify(hadesService).build(any());
        }

        @Test
        void triggerBuild_whenBuildFails_throwsContinuousIntegrationException() throws ContinuousIntegrationException {
            when(gitService.getLastCommitHash(any(LocalVCRepositoryUri.class))).thenReturn("some-hash");
            when(hadesService.build(any())).thenThrow(new ContinuousIntegrationException("build failed"));

            assertThatExceptionOfType(ContinuousIntegrationException.class).isThrownBy(() -> hadesTriggerService.triggerBuild(participation, null, null));
        }

        @Test
        void triggerBuild_withDeclaredResultPath_derivesIngestDirectoryFromIt() throws ContinuousIntegrationException {
            var phase = new BuildPhaseDTO("test", "mvn test", BuildPhaseCondition.ALWAYS, false, List.of("**/target/surefire-reports/*.xml"));
            when(buildPhaseEvaluationService.determineActiveBuildPhases(any(), any())).thenReturn(List.of(phase));
            when(gitService.getLastCommitHash(any(LocalVCRepositoryUri.class))).thenReturn("some-hash");
            when(buildScriptProviderService.replaceResultPathsPlaceholders(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
            ArgumentCaptor<BuildTriggerRequestDTO> captor = ArgumentCaptor.forClass(BuildTriggerRequestDTO.class);

            hadesTriggerService.triggerBuild(participation, null, null);

            verify(hadesService).build(captor.capture());
            assertThat(captor.getValue().additionalProperties()).containsEntry("resultIngestDirectory", "/shared/target/surefire-reports");
        }

        @Test
        void triggerBuild_withMidPathWildcard_stripsWildcardSegment() throws ContinuousIntegrationException {
            var phase = new BuildPhaseDTO("test", "pytest", BuildPhaseCondition.ALWAYS, false, List.of("src/**/test-results/*.xml"));
            when(buildPhaseEvaluationService.determineActiveBuildPhases(any(), any())).thenReturn(List.of(phase));
            when(gitService.getLastCommitHash(any(LocalVCRepositoryUri.class))).thenReturn("some-hash");
            when(buildScriptProviderService.replaceResultPathsPlaceholders(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
            ArgumentCaptor<BuildTriggerRequestDTO> captor = ArgumentCaptor.forClass(BuildTriggerRequestDTO.class);

            hadesTriggerService.triggerBuild(participation, null, null);

            verify(hadesService).build(captor.capture());
            assertThat(captor.getValue().additionalProperties()).containsEntry("resultIngestDirectory", "/shared/src/test-results");
        }

        @Test
        void triggerBuild_withPlaceholderResultPath_resolvesPlaceholderBeforeDerivingDirectory() throws ContinuousIntegrationException {
            var phase = new BuildPhaseDTO("test", "go test", BuildPhaseCondition.ALWAYS, false, List.of("${testWorkingDirectory}/test-results.xml"));
            when(buildPhaseEvaluationService.determineActiveBuildPhases(any(), any())).thenReturn(List.of(phase));
            when(gitService.getLastCommitHash(any(LocalVCRepositoryUri.class))).thenReturn("some-hash");
            when(buildScriptProviderService.replaceResultPathsPlaceholders(List.of("${testWorkingDirectory}/test-results.xml"), buildConfig))
                    .thenReturn(List.of("tests/test-results.xml"));
            ArgumentCaptor<BuildTriggerRequestDTO> captor = ArgumentCaptor.forClass(BuildTriggerRequestDTO.class);

            hadesTriggerService.triggerBuild(participation, null, null);

            verify(hadesService).build(captor.capture());
            assertThat(captor.getValue().additionalProperties()).containsEntry("resultIngestDirectory", "/shared/tests");
        }

        @Test
        void triggerBuild_withoutResultPathButMavenProjectType_guessesSurefireDirectory() throws ContinuousIntegrationException {
            exercise.setProjectType(ProjectType.PLAIN_MAVEN);
            when(gitService.getLastCommitHash(any(LocalVCRepositoryUri.class))).thenReturn("some-hash");
            ArgumentCaptor<BuildTriggerRequestDTO> captor = ArgumentCaptor.forClass(BuildTriggerRequestDTO.class);

            hadesTriggerService.triggerBuild(participation, null, null);

            verify(hadesService).build(captor.capture());
            assertThat(captor.getValue().additionalProperties()).containsEntry("resultIngestDirectory", "/shared/target/surefire-reports");
        }

        @Test
        void triggerBuild_withoutResultPathOrMavenProjectType_fallsBackToGradleDefault() throws ContinuousIntegrationException {
            when(gitService.getLastCommitHash(any(LocalVCRepositoryUri.class))).thenReturn("some-hash");
            ArgumentCaptor<BuildTriggerRequestDTO> captor = ArgumentCaptor.forClass(BuildTriggerRequestDTO.class);

            hadesTriggerService.triggerBuild(participation, null, null);

            verify(hadesService).build(captor.capture());
            assertThat(captor.getValue().additionalProperties()).containsEntry("resultIngestDirectory", "/shared/build/test-results/test");
        }
    }

    @Nested
    class GetBuildScriptTests {

        @Mock
        private ProgrammingExerciseParticipation participation;

        private ProgrammingExercise exercise;

        private ProgrammingExerciseBuildConfig buildConfig;

        @BeforeEach
        void setUp() {
            exercise = new ProgrammingExercise();
            exercise.setId(10L);
            exercise.setProgrammingLanguage(ProgrammingLanguage.JAVA);
            buildConfig = new ProgrammingExerciseBuildConfig();
            buildConfig.setBuildPlanConfiguration(null);
        }

        @Test
        void getBuildScript_withNullBuildPlanConfig_usesDefaultPhasesAndReturnsRenderedScript() {
            var phase = new BuildPhaseDTO("compile", "mvn compile", BuildPhaseCondition.ALWAYS, false, null);
            when(buildPhasesTemplateService.getDefaultBuildPlanPhasesFor(exercise)).thenReturn(List.of(phase));
            when(buildPhaseEvaluationService.determineActiveBuildPhases(List.of(phase), participation)).thenReturn(List.of(phase));
            when(localCIBuildConfigurationService.createBuildScriptFromActivePhases(buildConfig, List.of(phase), "/shared")).thenReturn("rendered-script");

            String script = hadesTriggerService.getBuildScript(buildConfig, participation, exercise);

            assertThat(script).isEqualTo("rendered-script");
        }

        @Test
        void getBuildScript_delegatesRenderingToLocalCIBuildConfigurationService() {
            var compile = new BuildPhaseDTO("compile", "mvn compile", BuildPhaseCondition.ALWAYS, false, null);
            // forceRun=true: HadesTriggerService no longer needs to know about this itself, it's LocalCIBuildConfigurationService's job
            var cleanup = new BuildPhaseDTO("cleanup", "rm -rf tmp", BuildPhaseCondition.ALWAYS, true, null);
            when(buildPhasesTemplateService.getDefaultBuildPlanPhasesFor(exercise)).thenReturn(List.of(compile, cleanup));
            when(buildPhaseEvaluationService.determineActiveBuildPhases(List.of(compile, cleanup), participation)).thenReturn(List.of(compile, cleanup));

            hadesTriggerService.getBuildScript(buildConfig, participation, exercise);

            verify(localCIBuildConfigurationService).createBuildScriptFromActivePhases(buildConfig, List.of(compile, cleanup), "/shared");
        }
    }
}
