package de.tum.cit.aet.artemis.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

import java.io.IOException;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.Locale;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import de.tum.cit.aet.artemis.account.api.AccountDemoApi;
import de.tum.cit.aet.artemis.account.api.AccountDemoApi.DemoUsers;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.course.api.CourseDemoApi;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.localci.domain.BuildJob;
import de.tum.cit.aet.artemis.localci.service.DockerClientTestService;
import de.tum.cit.aet.artemis.programming.api.ProgrammingDemoApi;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.util.RepositoryExportTestUtil;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalCILocalVCTest;

/**
 * Tests the demo programming exercise of the {@code demo} profile.
 * <p>
 * Creating a programming exercise needs a version control and a continuous integration system, so unlike the rest of the demo course it can only be seeded in the LocalVC and
 * LocalCI context. The test seeds the demo users and the demo course and then only the programming exercise through the demo APIs, because the other areas are covered in the
 * independent context already. Docker is mocked, so the test checks that the initial builds are triggered rather than what they produce, which the LocalCI tests cover.
 */
@Execution(ExecutionMode.SAME_THREAD)
class DemoProgrammingExerciseSeedingIntegrationTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    /**
     * The project key the demo exercise ends up with. It is fixed, so leftover repositories of an earlier run would make the creation fail with an existing project.
     */
    private static final String DEMO_PROJECT_KEY = (CourseDemoApi.DEMO_COURSE_SHORT_NAME + ProgrammingDemoApi.DEMO_EXERCISE_SHORT_NAME).toUpperCase(Locale.ROOT);

    @Autowired
    private AccountDemoApi accountDemoApi;

    @Autowired
    private CourseDemoApi courseDemoApi;

    @Autowired
    private ProgrammingDemoApi programmingDemoApi;

    @Value("${artemis.repo-clone-path}")
    private Path repoClonePath;

    @BeforeAll
    static void mockDockerClient() throws InterruptedException {
        dockerClientMock = DockerClientTestService.mockDockerClient();
    }

    /**
     * Lets the build agent use the mocked Docker client, like {@code AbstractProgrammingIntegrationLocalCILocalVCTestBase} does for the programming exercise tests.
     */
    @BeforeEach
    void useMockedDockerClient() {
        lenient().doReturn(dockerClientMock).when(buildAgentConfiguration).getDockerClient();
        lenient().doReturn(true).when(buildAgentConfiguration).isDockerAvailable();
    }

    @BeforeEach
    @AfterEach
    void deleteDemoRepositories() throws IOException {
        RepositoryExportTestUtil.deleteLocalVcProjectIfPresent(localVCBasePath, DEMO_PROJECT_KEY);
        // The server only copies the template into a repository whose working copy is empty, so stale working copies would leave the recreated repositories without commits.
        FileUtils.deleteDirectory(repoClonePath.resolve(DEMO_PROJECT_KEY).toFile());
    }

    @Test
    void seedsProgrammingExerciseThatStudentsCanStartRightAway() {
        ProgrammingExercise exercise = seedProgrammingExercise();

        assertThat(exercise.isVisibleToStudents()).as("the demo programming exercise is released").isTrue();
        assertThat(exercise.getDueDate()).as("the demo programming exercise is still open for submissions").isAfter(ZonedDateTime.now());
        assertThat(exercise.getProblemStatement()).as("the demo programming exercise ships the assignment of its template").isNotBlank();

        ProgrammingExercise withParticipations = programmingExerciseRepository.findWithAllParticipationsById(exercise.getId()).orElseThrow();
        localVCLocalCITestService.verifyRepositoryFoldersExist(withParticipations, localVCBasePath);
        // Setting up the exercise triggers the first builds of the template and the solution repository, which queues a build job for each of them.
        assertThat(buildJobRepository.findAll()).extracting(BuildJob::getParticipationId).as("the builds of the template and the solution repository were triggered")
                .contains(withParticipations.getTemplateParticipation().getId(), withParticipations.getSolutionParticipation().getId());
    }

    @Test
    void seedingTwiceKeepsTheExistingProgrammingExercise() {
        ProgrammingExercise exercise = seedProgrammingExercise();

        ProgrammingExercise afterSecondRun = seedProgrammingExercise();

        assertThat(afterSecondRun.getId()).as("an existing demo programming exercise is neither duplicated nor replaced").isEqualTo(exercise.getId());
        assertThat(programmingExerciseRepository.countByShortNameAndCourse(ProgrammingDemoApi.DEMO_EXERCISE_SHORT_NAME, exercise.getCourseViaExerciseGroupOrCourseMember()))
                .isOne();
    }

    private ProgrammingExercise seedProgrammingExercise() {
        DemoUsers users = accountDemoApi.createDemoUsers();
        Course course = courseDemoApi.createDemo(users.students(), users.tutor(), users.editor(), users.instructor());
        return SecurityUtils.runAs(users.instructor(), () -> programmingDemoApi.createDemo(course)).orElseThrow();
    }
}
