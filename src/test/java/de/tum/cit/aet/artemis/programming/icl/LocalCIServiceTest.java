package de.tum.cit.aet.artemis.programming.icl;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hazelcast.collection.IQueue;
import com.hazelcast.map.IMap;

import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentDTO;
import de.tum.cit.aet.artemis.buildagent.dto.BuildConfig;
import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;
import de.tum.cit.aet.artemis.buildagent.dto.JobTimingInfo;
import de.tum.cit.aet.artemis.buildagent.dto.RepositoryInfo;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationLocalCILocalVCTest;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.dto.CheckoutDirectoriesDTO;
import de.tum.cit.aet.artemis.programming.dto.aeolus.Windfile;
import de.tum.cit.aet.artemis.programming.service.RepositoryCheckoutService;
import de.tum.cit.aet.artemis.programming.service.ci.ContinuousIntegrationService.BuildStatus;

class LocalCIServiceTest extends AbstractProgrammingIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "localciservice";

    protected IQueue<BuildJobQueueItem> queuedJobs;

    protected IMap<Long, BuildJobQueueItem> processingJobs;

    @Autowired
    private RepositoryCheckoutService repositoryCheckoutService;

    @BeforeEach
    void setUp() {
        queuedJobs = hazelcastInstance.getQueue("buildJobQueue");
        processingJobs = hazelcastInstance.getMap("processingJobs");

        // remove listener to avoid triggering build job processing
        sharedQueueProcessingService.removeListenerAndCancelScheduledFuture();
    }

    @AfterEach
    void tearDown() {
        queuedJobs.clear();
        processingJobs.clear();

        // init to activate queue listener again
        sharedQueueProcessingService.init();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testReturnCorrectBuildStatus() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 1);
        Course course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        ProgrammingExercise exercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        ProgrammingExerciseStudentParticipation participation = participationUtilService.addStudentParticipationForProgrammingExercise(exercise, TEST_PREFIX + "student1");

        JobTimingInfo jobTimingInfo = new JobTimingInfo(ZonedDateTime.now(), ZonedDateTime.now().plusMinutes(1), ZonedDateTime.now().plusMinutes(2), null, 0);
        BuildConfig buildConfig = new BuildConfig("echo 'test'", "test", "test", "test", "test", "test", null, null, false, false, null, 0, null, null, null, null);
        RepositoryInfo repositoryInfo = new RepositoryInfo("test", null, RepositoryType.USER, "test", "test", "test", null, null);

        String memberAddress = hazelcastInstance.getCluster().getLocalMember().getAddress().toString();
        BuildAgentDTO buildAgent = new BuildAgentDTO("artemis-build-agent-test", memberAddress, "artemis-build-agent-test");

        BuildJobQueueItem job1 = new BuildJobQueueItem("1", "job1", buildAgent, participation.getId(), course.getId(), 1, 1, 1,
                de.tum.cit.aet.artemis.programming.domain.build.BuildStatus.SUCCESSFUL, repositoryInfo, jobTimingInfo, buildConfig, null);
        BuildJobQueueItem job2 = new BuildJobQueueItem("2", "job2", buildAgent, participation.getId(), course.getId(), 1, 1, 1,
                de.tum.cit.aet.artemis.programming.domain.build.BuildStatus.SUCCESSFUL, repositoryInfo, jobTimingInfo, buildConfig, null);

        queuedJobs = hazelcastInstance.getQueue("buildJobQueue");
        processingJobs = hazelcastInstance.getMap("processingJobs");

        // No build jobs for the participation are queued or building
        assertThat(continuousIntegrationService.getBuildStatus(participation)).isEqualTo(BuildStatus.INACTIVE);

        queuedJobs.add(job1);
        processingJobs.put(1L, job2);

        // At least one build job for the participation is queued
        assertThat(continuousIntegrationService.getBuildStatus(participation)).isEqualTo(BuildStatus.QUEUED);
        queuedJobs.clear();

        // No build jobs for the participation are queued, but at least one is building
        assertThat(continuousIntegrationService.getBuildStatus(participation)).isEqualTo(BuildStatus.BUILDING);
    }

    @Test
    void testHealth() {
        var health = continuousIntegrationService.health();
        assertThat(health.isUp()).isTrue();
    }

    @Test
    void testRecreateBuildPlanForExercise() throws IOException {
        String script = "echo 'Hello, World!'";
        Course course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        ProgrammingExercise exercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        exercise.getBuildConfig().setBuildScript(script);
        exercise.getBuildConfig().setBuildPlanConfiguration(null);
        continuousIntegrationService.recreateBuildPlansForExercise(exercise);
        script = buildScriptProviderService.getScriptFor(exercise.getProgrammingLanguage(), Optional.ofNullable(exercise.getProjectType()), exercise.isStaticCodeAnalysisEnabled(),
                exercise.getBuildConfig().hasSequentialTestRuns());
        Windfile windfile = aeolusTemplateService.getDefaultWindfileFor(exercise);
        String actualBuildConfig = exercise.getBuildConfig().getBuildPlanConfiguration();
        String expectedBuildConfig = new ObjectMapper().writeValueAsString(windfile);
        assertThat(actualBuildConfig).isEqualTo(expectedBuildConfig);
        assertThat(exercise.getBuildConfig().getBuildScript()).isEqualTo(script);
        // test that the method does not throw an exception when the exercise is null
        continuousIntegrationService.recreateBuildPlansForExercise(null);
    }

    @Test
    void testGetScriptForWithoutCache() {
        ReflectionTestUtils.setField(buildScriptProviderService, "scriptCache", new ConcurrentHashMap<>());
        ProgrammingExercise programmingExercise = new ProgrammingExercise();
        programmingExercise.setBuildConfig(new ProgrammingExerciseBuildConfig());
        programmingExercise.setProgrammingLanguage(ProgrammingLanguage.HASKELL);
        programmingExercise.setProjectType(null);
        programmingExercise.setStaticCodeAnalysisEnabled(false);
        programmingExercise.getBuildConfig().setSequentialTestRuns(false);
        String script = buildScriptProviderService.getScriptFor(programmingExercise);
        assertThat(script).isNotNull();
    }

    @Test
    void testUnsupportedMethods() {
        continuousIntegrationService.givePlanPermissions(null, null);
        continuousIntegrationService.enablePlan(null, null);
        continuousIntegrationService.updatePlanRepository(null, null, null, null, null, null, null);
        assertThat(continuousIntegrationService.getPlanKey(null)).isNull();
    }

    @Nested
    class GetCheckoutDirectoriesTests {

        @Test
        void getCheckoutDirectoriesForJava() {
            CheckoutDirectoriesDTO checkoutDirectories = repositoryCheckoutService.getCheckoutDirectories(ProgrammingLanguage.JAVA, true);

            // Verify submission build plan checkout directories
            assertThat(checkoutDirectories.submissionBuildPlanCheckoutDirectories().exerciseCheckoutDirectory()).isEqualTo("/assignment");
            assertThat(checkoutDirectories.submissionBuildPlanCheckoutDirectories().solutionCheckoutDirectory()).isNull();
            assertThat(checkoutDirectories.submissionBuildPlanCheckoutDirectories().testCheckoutDirectory()).isEqualTo("/");

            // Verify solution build plan checkout directories
            assertThat(checkoutDirectories.solutionBuildPlanCheckoutDirectories().exerciseCheckoutDirectory()).isEqualTo(null);
            assertThat(checkoutDirectories.solutionBuildPlanCheckoutDirectories().solutionCheckoutDirectory()).isEqualTo("/assignment");
            assertThat(checkoutDirectories.solutionBuildPlanCheckoutDirectories().testCheckoutDirectory()).isEqualTo("/");
        }

        @Test
        void getCheckoutDirectoriesForOcaml() {
            CheckoutDirectoriesDTO checkoutDirectories = repositoryCheckoutService.getCheckoutDirectories(ProgrammingLanguage.OCAML, true);

            // Verify submission build plan checkout directories
            assertThat(checkoutDirectories.submissionBuildPlanCheckoutDirectories().exerciseCheckoutDirectory()).isEqualTo("/assignment");
            assertThat(checkoutDirectories.submissionBuildPlanCheckoutDirectories().solutionCheckoutDirectory()).isEqualTo("/solution");
            assertThat(checkoutDirectories.submissionBuildPlanCheckoutDirectories().testCheckoutDirectory()).isEqualTo("/tests");

            // Verify solution build plan checkout directories
            assertThat(checkoutDirectories.solutionBuildPlanCheckoutDirectories().exerciseCheckoutDirectory()).isNull();
            assertThat(checkoutDirectories.solutionBuildPlanCheckoutDirectories().solutionCheckoutDirectory()).isEqualTo("/assignment");
            assertThat(checkoutDirectories.solutionBuildPlanCheckoutDirectories().testCheckoutDirectory()).isEqualTo("/tests");
        }

        @Test
        void getCheckoutDirectoriesForOcamlWithoutCheckingOutSolution() {
            CheckoutDirectoriesDTO checkoutDirectories = repositoryCheckoutService.getCheckoutDirectories(ProgrammingLanguage.OCAML, false);

            // Verify submission build plan checkout directories
            assertThat(checkoutDirectories.submissionBuildPlanCheckoutDirectories().exerciseCheckoutDirectory()).isEqualTo("/assignment");
            assertThat(checkoutDirectories.submissionBuildPlanCheckoutDirectories().solutionCheckoutDirectory()).isNull();
            assertThat(checkoutDirectories.submissionBuildPlanCheckoutDirectories().testCheckoutDirectory()).isEqualTo("/tests");

            // Verify solution build plan checkout directories
            assertThat(checkoutDirectories.solutionBuildPlanCheckoutDirectories().exerciseCheckoutDirectory()).isNull();
            assertThat(checkoutDirectories.solutionBuildPlanCheckoutDirectories().solutionCheckoutDirectory()).isEqualTo("/assignment");
            assertThat(checkoutDirectories.solutionBuildPlanCheckoutDirectories().testCheckoutDirectory()).isEqualTo("/tests");
        }
    }
}
