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

import de.tum.cit.aet.artemis.buildagent.dto.DockerFlagsDTO;
import de.tum.cit.aet.artemis.localci.service.BuildPhaseEvaluationService;
import de.tum.cit.aet.artemis.localci.service.BuildPhasesTemplateService;
import de.tum.cit.aet.artemis.localci.service.BuildScriptProviderService;
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
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseBuildConfigService;
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
    private BuildScriptProviderService buildScriptProviderService;

    @Mock
    private ProgrammingExerciseBuildConfigService programmingExerciseBuildConfigService;

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
            ArgumentCaptor<BuildTriggerRequestDTO> captor = ArgumentCaptor.forClass(BuildTriggerRequestDTO.class);

            hadesTriggerService.triggerBuild(participation, "commit-abc", null);

            verify(hadesService).build(captor.capture());
            verify(gitService, never()).getLastCommitHash(exerciseRepoUri);
            assertThat(captor.getValue().exerciseRepository().commitHash()).isEqualTo("commit-abc");
            assertThat(captor.getValue().testRepository().commitHash()).isEqualTo("test-hash");
        }

        @Test
        void triggerBuild_withTestsRepositoryTrigger_usesCommitHashForTest() throws ContinuousIntegrationException {
            // when triggeredByPushTo=TESTS and commitHash is set, test hash comes from commitHash directly
            when(gitService.getLastCommitHash(exerciseRepoUri)).thenReturn("assign-hash");
            ArgumentCaptor<BuildTriggerRequestDTO> captor = ArgumentCaptor.forClass(BuildTriggerRequestDTO.class);

            hadesTriggerService.triggerBuild(participation, "test-commit-abc", RepositoryType.TESTS);

            verify(hadesService).build(captor.capture());
            assertThat(captor.getValue().testRepository().commitHash()).isEqualTo("test-commit-abc");
            assertThat(captor.getValue().exerciseRepository().commitHash()).isEqualTo("assign-hash");
        }

        @Test
        void triggerBuild_whenBuildFails_throwsContinuousIntegrationException() throws ContinuousIntegrationException {
            when(gitService.getLastCommitHash(any(LocalVCRepositoryUri.class))).thenReturn("some-hash");
            when(hadesService.build(any())).thenThrow(new ContinuousIntegrationException("build failed"));

            assertThatExceptionOfType(ContinuousIntegrationException.class).isThrownBy(() -> hadesTriggerService.triggerBuild(participation, null, null));
        }

        @Test
        void triggerBuild_withLiteralResultPath_derivesNarrowDirectory() throws ContinuousIntegrationException {
            // A result path with a literal (wildcard-free) directory prefix resolves to that exact directory.
            exercise.setProgrammingLanguage(ProgrammingLanguage.PYTHON);
            var phase = new BuildPhaseDTO("test", "pytest", BuildPhaseCondition.ALWAYS, false, List.of("reports/junit/*.xml"));
            when(buildPhaseEvaluationService.determineActiveBuildPhases(any(), any())).thenReturn(List.of(phase));
            when(gitService.getLastCommitHash(any(LocalVCRepositoryUri.class))).thenReturn("some-hash");
            when(buildScriptProviderService.replaceResultPathsPlaceholders(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
            ArgumentCaptor<BuildTriggerRequestDTO> captor = ArgumentCaptor.forClass(BuildTriggerRequestDTO.class);

            hadesTriggerService.triggerBuild(participation, null, null);

            verify(hadesService).build(captor.capture());
            assertThat(captor.getValue().additionalProperties()).containsEntry("resultIngestDirectory", "/shared/reports/junit");
        }

        @Test
        void triggerBuild_withLeadingWildcardResultPath_usesRecursiveRoot() throws ContinuousIntegrationException {
            // A leading "**" means the results live at an unknown depth; the recursive parser locates them from the working
            // directory root. This is the shape shipped by the real Gradle template (**/test-results/test/*.xml).
            var phase = new BuildPhaseDTO("test", "./gradlew test", BuildPhaseCondition.ALWAYS, false, List.of("**/test-results/test/*.xml"));
            when(buildPhaseEvaluationService.determineActiveBuildPhases(any(), any())).thenReturn(List.of(phase));
            when(gitService.getLastCommitHash(any(LocalVCRepositoryUri.class))).thenReturn("some-hash");
            when(buildScriptProviderService.replaceResultPathsPlaceholders(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
            ArgumentCaptor<BuildTriggerRequestDTO> captor = ArgumentCaptor.forClass(BuildTriggerRequestDTO.class);

            hadesTriggerService.triggerBuild(participation, null, null);

            verify(hadesService).build(captor.capture());
            assertThat(captor.getValue().additionalProperties()).containsEntry("resultIngestDirectory", "/shared");
        }

        @Test
        void triggerBuild_withMidPathWildcard_stopsAtWildcardSegment() throws ContinuousIntegrationException {
            // The ingest directory is the literal prefix up to the first wildcard segment: "src/**/test-results/*.xml" -> "src".
            exercise.setProgrammingLanguage(ProgrammingLanguage.PYTHON);
            var phase = new BuildPhaseDTO("test", "pytest", BuildPhaseCondition.ALWAYS, false, List.of("src/**/test-results/*.xml"));
            when(buildPhaseEvaluationService.determineActiveBuildPhases(any(), any())).thenReturn(List.of(phase));
            when(gitService.getLastCommitHash(any(LocalVCRepositoryUri.class))).thenReturn("some-hash");
            when(buildScriptProviderService.replaceResultPathsPlaceholders(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
            ArgumentCaptor<BuildTriggerRequestDTO> captor = ArgumentCaptor.forClass(BuildTriggerRequestDTO.class);

            hadesTriggerService.triggerBuild(participation, null, null);

            verify(hadesService).build(captor.capture());
            assertThat(captor.getValue().additionalProperties()).containsEntry("resultIngestDirectory", "/shared/src");
        }

        @Test
        void triggerBuild_withPlaceholderResultPath_resolvesPlaceholderBeforeDerivingDirectory() throws ContinuousIntegrationException {
            exercise.setProgrammingLanguage(ProgrammingLanguage.GO);
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

        @Test
        void triggerBuild_withSequentialMavenResultPaths_derivesCommonAncestor() throws ContinuousIntegrationException {
            // Sequential Maven writes surefire reports under both structural/target and behavior/target (real template glob
            // "**/target/surefire-reports/*.xml"), so the ingest directory must be the common ancestor /shared for the
            // recursive parser to find both suites.
            exercise.setProjectType(ProjectType.PLAIN_MAVEN);
            var structural = new BuildPhaseDTO("structural", "cd structural && mvn test", BuildPhaseCondition.ALWAYS, false, List.of("**/target/surefire-reports/*.xml"));
            var behavior = new BuildPhaseDTO("behavior", "cd behavior && mvn test", BuildPhaseCondition.ALWAYS, false, List.of("**/target/surefire-reports/*.xml"));
            when(buildPhaseEvaluationService.determineActiveBuildPhases(any(), any())).thenReturn(List.of(structural, behavior));
            when(gitService.getLastCommitHash(any(LocalVCRepositoryUri.class))).thenReturn("some-hash");
            when(buildScriptProviderService.replaceResultPathsPlaceholders(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
            ArgumentCaptor<BuildTriggerRequestDTO> captor = ArgumentCaptor.forClass(BuildTriggerRequestDTO.class);

            hadesTriggerService.triggerBuild(participation, null, null);

            verify(hadesService).build(captor.capture());
            assertThat(captor.getValue().additionalProperties()).containsEntry("resultIngestDirectory", "/shared");
        }

        @Test
        void triggerBuild_withSequentialGradleResultPaths_usesRecursiveRoot() throws ContinuousIntegrationException {
            // Sequential Gradle uses the real template globs "**/test-results/structuralTests/*.xml" and
            // "**/test-results/behaviorTests/*.xml"; both start with "**", so the ingest directory is the recursive root /shared.
            var structural = new BuildPhaseDTO("structural_tests", "./gradlew structuralTests", BuildPhaseCondition.ALWAYS, false,
                    List.of("**/test-results/structuralTests/*.xml"));
            var behavior = new BuildPhaseDTO("behavior_tests", "./gradlew behaviorTests", BuildPhaseCondition.ALWAYS, false, List.of("**/test-results/behaviorTests/*.xml"));
            when(buildPhaseEvaluationService.determineActiveBuildPhases(any(), any())).thenReturn(List.of(structural, behavior));
            when(gitService.getLastCommitHash(any(LocalVCRepositoryUri.class))).thenReturn("some-hash");
            when(buildScriptProviderService.replaceResultPathsPlaceholders(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
            ArgumentCaptor<BuildTriggerRequestDTO> captor = ArgumentCaptor.forClass(BuildTriggerRequestDTO.class);

            hadesTriggerService.triggerBuild(participation, null, null);

            verify(hadesService).build(captor.capture());
            assertThat(captor.getValue().additionalProperties()).containsEntry("resultIngestDirectory", "/shared");
        }

        @Test
        void triggerBuild_withMavenBlackboxResultPaths_usesRecursiveRoot() throws ContinuousIntegrationException {
            // Maven blackbox uses the real template glob "**/customFeedbacks/TEST-*.json"; the leading "**" resolves to the
            // recursive root /shared.
            var secret = new BuildPhaseDTO("secret_tests", "runtest secret", BuildPhaseCondition.ALWAYS, false, List.of("**/customFeedbacks/TEST-*.json"));
            var publicTests = new BuildPhaseDTO("public_tests", "runtest public", BuildPhaseCondition.ALWAYS, false, List.of("**/customFeedbacks/TEST-*.json"));
            when(buildPhaseEvaluationService.determineActiveBuildPhases(any(), any())).thenReturn(List.of(secret, publicTests));
            when(gitService.getLastCommitHash(any(LocalVCRepositoryUri.class))).thenReturn("some-hash");
            when(buildScriptProviderService.replaceResultPathsPlaceholders(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
            ArgumentCaptor<BuildTriggerRequestDTO> captor = ArgumentCaptor.forClass(BuildTriggerRequestDTO.class);

            hadesTriggerService.triggerBuild(participation, null, null);

            verify(hadesService).build(captor.capture());
            assertThat(captor.getValue().additionalProperties()).containsEntry("resultIngestDirectory", "/shared");
        }

        @Test
        void triggerBuild_withoutConfiguredCheckoutPaths_usesLanguageDefaults() throws ContinuousIntegrationException {
            // JAVA test checkout default is an empty string; the assignment falls back to "assignment".
            when(gitService.getLastCommitHash(any(LocalVCRepositoryUri.class))).thenReturn("some-hash");
            ArgumentCaptor<BuildTriggerRequestDTO> captor = ArgumentCaptor.forClass(BuildTriggerRequestDTO.class);

            hadesTriggerService.triggerBuild(participation, null, null);

            verify(hadesService).build(captor.capture());
            assertThat(captor.getValue().exerciseRepository().cloneLocation()).isEqualTo("assignment");
            assertThat(captor.getValue().testRepository().cloneLocation()).isEqualTo("");
        }

        @Test
        void triggerBuild_withConfiguredCheckoutPaths_usesBuildConfigPaths() throws ContinuousIntegrationException {
            // Imported exercises keep custom checkout paths, which must flow into the clone metadata.
            buildConfig.setAssignmentCheckoutPath("custom-assignment");
            buildConfig.setTestCheckoutPath("custom-tests");
            when(gitService.getLastCommitHash(any(LocalVCRepositoryUri.class))).thenReturn("some-hash");
            ArgumentCaptor<BuildTriggerRequestDTO> captor = ArgumentCaptor.forClass(BuildTriggerRequestDTO.class);

            hadesTriggerService.triggerBuild(participation, null, null);

            verify(hadesService).build(captor.capture());
            assertThat(captor.getValue().exerciseRepository().cloneLocation()).isEqualTo("custom-assignment");
            assertThat(captor.getValue().testRepository().cloneLocation()).isEqualTo("custom-tests");
        }

        @Test
        void triggerBuild_withCustomDockerImageInWindfile_passesItThrough() throws ContinuousIntegrationException {
            buildConfig.setBuildPlanConfiguration("{\"dockerImage\":\"ghcr.io/example/custom-image:1.0\"}");
            when(gitService.getLastCommitHash(any(LocalVCRepositoryUri.class))).thenReturn("some-hash");
            ArgumentCaptor<BuildTriggerRequestDTO> captor = ArgumentCaptor.forClass(BuildTriggerRequestDTO.class);

            hadesTriggerService.triggerBuild(participation, null, null);

            verify(hadesService).build(captor.capture());
            assertThat(captor.getValue().dockerImage()).isEqualTo("ghcr.io/example/custom-image:1.0");
        }

        @Test
        void triggerBuild_withoutCustomDockerImage_leavesImageNullForDefaultResolution() throws ContinuousIntegrationException {
            when(gitService.getLastCommitHash(any(LocalVCRepositoryUri.class))).thenReturn("some-hash");
            ArgumentCaptor<BuildTriggerRequestDTO> captor = ArgumentCaptor.forClass(BuildTriggerRequestDTO.class);

            hadesTriggerService.triggerBuild(participation, null, null);

            verify(hadesService).build(captor.capture());
            assertThat(captor.getValue().dockerImage()).isNull();
        }

        @Test
        void triggerBuild_withBlankDockerImageInWindfile_leavesImageNull() throws ContinuousIntegrationException {
            buildConfig.setBuildPlanConfiguration("{\"dockerImage\":\"   \"}");
            when(gitService.getLastCommitHash(any(LocalVCRepositoryUri.class))).thenReturn("some-hash");
            ArgumentCaptor<BuildTriggerRequestDTO> captor = ArgumentCaptor.forClass(BuildTriggerRequestDTO.class);

            hadesTriggerService.triggerBuild(participation, null, null);

            verify(hadesService).build(captor.capture());
            assertThat(captor.getValue().dockerImage()).isNull();
        }

        @Test
        void triggerBuild_withTimeoutAndDockerFlags_carriesThemInBuildTriggerRequest() throws ContinuousIntegrationException {
            buildConfig.setTimeoutSeconds(600);
            var dockerFlags = new DockerFlagsDTO("none", java.util.Map.of("FOO", "bar"), 2, 2048, 4096);
            when(programmingExerciseBuildConfigService.parseDockerFlags(buildConfig)).thenReturn(dockerFlags);
            when(gitService.getLastCommitHash(any(LocalVCRepositoryUri.class))).thenReturn("some-hash");
            ArgumentCaptor<BuildTriggerRequestDTO> captor = ArgumentCaptor.forClass(BuildTriggerRequestDTO.class);

            hadesTriggerService.triggerBuild(participation, null, null);

            verify(hadesService).build(captor.capture());
            assertThat(captor.getValue().timeoutSeconds()).isEqualTo(600);
            assertThat(captor.getValue().dockerFlags()).isSameAs(dockerFlags);
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
        void getBuildScript_withNullBuildPlanConfig_usesDefaultPhasesAndReturnsPlaceholderResolvedScript() {
            var phase = new BuildPhaseDTO("compile", "mvn compile", BuildPhaseCondition.ALWAYS, false, null);
            when(buildPhasesTemplateService.getDefaultBuildPlanPhasesFor(exercise)).thenReturn(List.of(phase));
            when(buildPhaseEvaluationService.determineActiveBuildPhases(List.of(phase), participation)).thenReturn(List.of(phase));
            when(buildScriptProviderService.replacePlaceholders(any(), any(), any(), any())).thenReturn("resolved-script");

            String script = hadesTriggerService.getBuildScript(buildConfig, participation, exercise);

            assertThat(script).isEqualTo("resolved-script");
        }

        @Test
        void getBuildScript_resetsWorkingDirectoryBetweenPhasesSoRelativeCdDoesNotLeak() {
            var structural = new BuildPhaseDTO("structural", "cd \"structural\" && mvn test", BuildPhaseCondition.ALWAYS, false, null);
            var behavior = new BuildPhaseDTO("behavior", "cd \"behavior\" && mvn test", BuildPhaseCondition.ALWAYS, false, null);
            when(buildPhasesTemplateService.getDefaultBuildPlanPhasesFor(exercise)).thenReturn(List.of(structural, behavior));
            when(buildPhaseEvaluationService.determineActiveBuildPhases(List.of(structural, behavior), participation)).thenReturn(List.of(structural, behavior));
            when(buildScriptProviderService.replacePlaceholders(any(), any(), any(), any())).thenAnswer(invocation -> invocation.getArgument(0));

            String script = hadesTriggerService.getBuildScript(buildConfig, participation, exercise);

            // Each phase is a Bash function whose body first resets to /shared so a relative cd cannot leak into the next phase.
            assertThat(script).isEqualTo("""
                    set -e
                    phase_0() {
                    cd /shared
                    cd "structural" && mvn test
                    }
                    phase_1() {
                    cd /shared
                    cd "behavior" && mvn test
                    }
                    phase_0
                    phase_1""");
        }

        @Test
        void getBuildScript_wrapsPhaseScriptsInFunctionsSoLocalIsLegal() {
            // A MAVEN_BLACKBOX-style phase uses `local`, which is only legal inside a function. Rendering the phase as a function
            // body keeps it valid Bash.
            var phase = new BuildPhaseDTO("replace_script_variables", "local JAVA_FLAGS=\"\"\nsed -i \"s#JAVA_FLAGS#${JAVA_FLAGS}#\" config.exp", BuildPhaseCondition.ALWAYS, false,
                    null);
            when(buildPhasesTemplateService.getDefaultBuildPlanPhasesFor(exercise)).thenReturn(List.of(phase));
            when(buildPhaseEvaluationService.determineActiveBuildPhases(List.of(phase), participation)).thenReturn(List.of(phase));
            when(buildScriptProviderService.replacePlaceholders(any(), any(), any(), any())).thenAnswer(invocation -> invocation.getArgument(0));

            String script = hadesTriggerService.getBuildScript(buildConfig, participation, exercise);

            assertThat(script).isEqualTo("""
                    set -e
                    phase_0() {
                    cd /shared
                    local JAVA_FLAGS=""
                    sed -i "s#JAVA_FLAGS#${JAVA_FLAGS}#" config.exp
                    }
                    phase_0""");
        }

        @Test
        void getBuildScript_withForceRunPhase_alwaysRunsForceRunAfterButExitsWithBuildResult() {
            var test = new BuildPhaseDTO("test", "mvn test", BuildPhaseCondition.ALWAYS, false, null);
            var sanitize = new BuildPhaseDTO("sanitize_feedback", "sed -i 's/x/y/g' customFeedbacks/*.json || true", BuildPhaseCondition.ALWAYS, true, null);
            when(buildPhasesTemplateService.getDefaultBuildPlanPhasesFor(exercise)).thenReturn(List.of(test, sanitize));
            when(buildPhaseEvaluationService.determineActiveBuildPhases(List.of(test, sanitize), participation)).thenReturn(List.of(test, sanitize));
            when(buildScriptProviderService.replacePlaceholders(any(), any(), any(), any())).thenAnswer(invocation -> invocation.getArgument(0));

            String script = hadesTriggerService.getBuildScript(buildConfig, participation, exercise);

            assertThat(script).isEqualTo("""
                    phase_0() {
                    cd /shared
                    mvn test
                    }
                    phase_1() {
                    cd /shared
                    sed -i 's/x/y/g' customFeedbacks/*.json || true
                    }
                    (
                    set -e
                    phase_0
                    )
                    build_exit_code=$?
                    phase_1
                    exit ${build_exit_code}""");
        }
    }
}
