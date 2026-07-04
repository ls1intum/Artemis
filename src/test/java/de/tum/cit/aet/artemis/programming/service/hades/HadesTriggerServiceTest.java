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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tum.cit.aet.artemis.localci.service.BuildPhaseEvaluationService;
import de.tum.cit.aet.artemis.localci.service.BuildPhasesTemplateService;
import de.tum.cit.aet.artemis.localvc.service.GitService;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.build.BuildPhaseCondition;
import de.tum.cit.aet.artemis.programming.dto.BuildPhaseDTO;
import de.tum.cit.aet.artemis.programming.exception.ContinuousIntegrationException;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;

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
        void getBuildScript_withNullBuildPlanConfig_usesDefaultPhases() {
            var phase = new BuildPhaseDTO("compile", "mvn compile", BuildPhaseCondition.ALWAYS, false, null);
            when(buildPhasesTemplateService.getDefaultBuildPlanPhasesFor(exercise)).thenReturn(List.of(phase));
            when(buildPhaseEvaluationService.determineActiveBuildPhases(any(), any())).thenReturn(List.of(phase));

            String script = hadesTriggerService.getBuildScript(buildConfig, participation, exercise);

            assertThat(script).startsWith("set -e && cd /shared && ");
            assertThat(script).contains("mvn compile");
        }

        @Test
        void getBuildScript_withMultiplePhases_concatenatesScripts() {
            var compile = new BuildPhaseDTO("compile", "mvn compile", BuildPhaseCondition.ALWAYS, false, null);
            var test = new BuildPhaseDTO("test", "mvn test", BuildPhaseCondition.ALWAYS, false, null);
            when(buildPhasesTemplateService.getDefaultBuildPlanPhasesFor(exercise)).thenReturn(List.of(compile, test));
            when(buildPhaseEvaluationService.determineActiveBuildPhases(any(), any())).thenReturn(List.of(compile, test));

            String script = hadesTriggerService.getBuildScript(buildConfig, participation, exercise);

            assertThat(script).isEqualTo("set -e && cd /shared && mvn compile && mvn test");
        }

        @Test
        void getBuildScript_withBlankScriptPhase_skipsEmptyScripts() {
            var blank = new BuildPhaseDTO("noop", "", BuildPhaseCondition.ALWAYS, false, null);
            when(buildPhasesTemplateService.getDefaultBuildPlanPhasesFor(exercise)).thenReturn(List.of(blank));
            when(buildPhaseEvaluationService.determineActiveBuildPhases(any(), any())).thenReturn(List.of(blank));

            String script = hadesTriggerService.getBuildScript(buildConfig, participation, exercise);

            assertThat(script).isEqualTo("set -e && cd /shared");
        }
    }
}
