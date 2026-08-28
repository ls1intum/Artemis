package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.buildagent.dto.SandboxExecResultDTO;
import de.tum.cit.aet.artemis.buildagent.dto.SandboxSessionContextDTO;
import de.tum.cit.aet.artemis.buildagent.service.InteractiveSandbox;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.hyperion.dto.GenerationMode;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.DifferentialVerificationService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.workspace.GenerationWorkspaceService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.workspace.SandboxBuildCommandService;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseFactory;

/** Executes the pre-provider readiness probe against the supported real Java build harnesses. */
@EnabledIf("de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.HyperionMockedLlmE2eSupport#isReadinessMatrixConfigured")
@Execution(ExecutionMode.SAME_THREAD)
@Isolated
class HyperionBuildReadinessDockerIntegrationTest extends AbstractHyperionMockedLlmEndToEndTest {

    private static final String TEST_PREFIX = "hypready";

    @Autowired
    private GenerationWorkspaceService workspace;

    @Autowired
    private DifferentialVerificationService verifier;

    @Autowired
    private Optional<InteractiveSandbox> interactiveSandbox;

    @Override
    protected String getTestPrefix() {
        return TEST_PREFIX;
    }

    private static Stream<Arguments> buildConfigurations() {
        return Stream.of(ProjectType.PLAIN_MAVEN, ProjectType.MAVEN_MAVEN, ProjectType.PLAIN_GRADLE, ProjectType.GRADLE_GRADLE)
                .flatMap(projectType -> Stream.of(Arguments.of(projectType, false), Arguments.of(projectType, true)));
    }

    @ParameterizedTest
    @MethodSource("buildConfigurations")
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void readinessUsesOnlyTheCanonicalFixture(ProjectType projectType, boolean sequentialTestRuns) throws Exception {
        ProgrammingExercise exercise = scaffoldExercise(projectType, sequentialTestRuns);
        poisonExerciseSources(exercise, sequentialTestRuns);
        InteractiveSandbox sandbox = interactiveSandbox.orElseThrow();
        SandboxSessionContextDTO context = new SandboxSessionContextDTO("readiness-" + projectType + "-" + sequentialTestRuns, exercise.getId(), exercise.getTitle(),
                exercise.getCourseViaExerciseGroupOrCourseMember().getId(), TEST_PREFIX + "instructor1", GenerationMode.ADAPT.name());
        String sessionId = sandbox.createSession(workspace.sessionSpec(exercise, context));
        try {
            workspace.seedWorkspace(sandbox, sessionId, exercise, GenerationMode.ADAPT);
            workspace.stageBuildReadinessFixture(sandbox, sessionId, exercise);

            assertThat(verifier.checkBuildEnvironment(sandbox, sessionId, exercise)).isEmpty();
            SandboxExecResultDTO fixtureRemoved = sandbox.exec(sessionId, java.time.Duration.ofSeconds(10), "sh", "-c",
                    "test -d " + SandboxBuildCommandService.READINESS_FIXTURE_DIR + " && test -z \"$(find " + SandboxBuildCommandService.READINESS_FIXTURE_DIR
                            + " -mindepth 1 -print -quit)\"");
            assertThat(fixtureRemoved.isSuccess()).as("the readiness fixture is empty before agent tools can run").isTrue();
        }
        finally {
            sandbox.destroySession(sessionId);
        }
    }

    private ProgrammingExercise scaffoldExercise(ProjectType projectType, boolean sequentialTestRuns) throws Exception {
        Course course = courseUtilService.addEmptyCourse();
        ProgrammingExercise exercise = ProgrammingExerciseFactory.generateProgrammingExercise(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(7), course,
                ProgrammingLanguage.JAVA);
        exercise.setProjectType(projectType);
        exercise.getBuildConfig().setSequentialTestRuns(sequentialTestRuns);
        exercise.setShortName("HR" + projectType.name().replace("PLAIN_", "").charAt(0) + (sequentialTestRuns ? "S" : "R"));
        exercise.setTitle("Hyperion readiness " + projectType + " " + sequentialTestRuns);
        exercise.setChannelName("hyp-ready-" + System.nanoTime());
        ProgrammingExercise created = creationService.createProgrammingExercise(exercise, true);
        return projectType.isMaven() ? useOfflineMavenPluginVersions(created) : created;
    }

    private void poisonExerciseSources(ProgrammingExercise exercise, boolean sequentialTestRuns) throws Exception {
        poisonRepository(exercise, RepositoryType.SOLUTION, "src/de/test/Broken.java");
        if (sequentialTestRuns) {
            poisonRepository(exercise, RepositoryType.TESTS, "behavior/test/de/test/BrokenBehaviorTest.java");
            poisonRepository(exercise, RepositoryType.TESTS, "structural/test/de/test/BrokenStructureTest.java");
        }
        else {
            poisonRepository(exercise, RepositoryType.TESTS, "test/de/test/BrokenTest.java");
        }
    }

    private void poisonRepository(ProgrammingExercise exercise, RepositoryType repositoryType, String relativePath) throws Exception {
        LocalVCRepositoryUri uri = exercise.getRepositoryURI(repositoryType);
        Repository repository = gitService.getOrCheckoutRepository(uri, true, localVCLocalCITestService.getDefaultBranch(), true);
        Path path = repository.getLocalPath().resolve(relativePath);
        Files.createDirectories(path.getParent());
        FileUtils.writeStringToFile(path.toFile(), "this is intentionally not Java", StandardCharsets.UTF_8);
        gitService.stageAllChanges(repository);
        gitService.commitAndPush(repository, "Poison exercise sources for readiness isolation", false, null);
    }
}
