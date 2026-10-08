package de.tum.cit.aet.artemis.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.lenient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.Optional;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import com.github.dockerjava.api.DockerClient;

import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyExerciseLink;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyExerciseLinkRepository;
import de.tum.cit.aet.artemis.course.api.CourseDemoApi;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.demo.service.DemoDataSeedingService;
import de.tum.cit.aet.artemis.lecture.domain.ExerciseUnit;
import de.tum.cit.aet.artemis.lecture.repository.LectureRepository;
import de.tum.cit.aet.artemis.localci.domain.BuildJob;
import de.tum.cit.aet.artemis.localci.service.DistributedDataAccessService;
import de.tum.cit.aet.artemis.localci.service.DockerClientTestService;
import de.tum.cit.aet.artemis.programming.api.ProgrammingDemoApi;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.util.RepositoryExportTestUtil;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalCILocalVCTest;

/**
 * Tests the demo programming exercise of the {@code demo} profile.
 * <p>
 * Creating a programming exercise needs a version control and a continuous integration system, so unlike the rest of the demo course it can only be seeded in the LocalVC and
 * LocalCI context. That is also the setup the documentation recommends for the demo profile, so this test seeds the whole demo course and checks that the programming exercise
 * takes part in it like the other exercises. Docker is mocked, so the test checks that the initial builds are triggered rather than what they produce, which the LocalCI tests
 * cover.
 */
@Execution(ExecutionMode.SAME_THREAD)
class DemoProgrammingExerciseSeedingIntegrationTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    /**
     * The project key the demo exercise ends up with. It is fixed, so leftover repositories of an earlier run would make the creation fail with an existing project.
     */
    private static final String DEMO_PROJECT_KEY = (CourseDemoApi.DEMO_COURSE_SHORT_NAME + ProgrammingDemoApi.DEMO_EXERCISE_SHORT_NAME).toUpperCase(Locale.ROOT);

    /**
     * The Docker client of the builds this test triggers. Its own rather than the shared static one of the base class, so that its builds cannot use up the stubs of a
     * programming exercise test that runs at the same time.
     */
    private static DockerClient demoDockerClient;

    @Autowired
    private DemoDataSeedingService demoDataSeedingService;

    @Autowired
    private LectureRepository lectureRepository;

    @Autowired
    private CompetencyExerciseLinkRepository competencyExerciseLinkRepository;

    @Autowired
    private DistributedDataAccessService distributedDataAccessService;

    @Value("${artemis.repo-clone-path}")
    private Path repoClonePath;

    @BeforeAll
    static void mockDockerClient() throws InterruptedException {
        demoDockerClient = DockerClientTestService.mockDockerClient();
    }

    /**
     * Lets the build agent use the mocked Docker client, like {@code AbstractProgrammingIntegrationLocalCILocalVCTestBase} does for the programming exercise tests, and starts
     * without repositories of an earlier run.
     */
    @BeforeEach
    void useMockedDockerClient() throws IOException {
        lenient().doReturn(demoDockerClient).when(buildAgentConfiguration).getDockerClient();
        lenient().doReturn(true).when(buildAgentConfiguration).isDockerAvailable();
        deleteDemoRepositories();
    }

    /**
     * Deletes the repositories of the demo programming exercise once the builds it triggered are over, so that no build runs against deleted repositories or blocks the build
     * agent for the tests that follow.
     */
    @AfterEach
    void deleteRepositoriesOnceTheBuildsAreOver() throws IOException {
        Optional<ProgrammingExercise> exercise = demoProgrammingExercise();
        if (exercise.isPresent()) {
            long courseId = exercise.get().getCourseViaExerciseGroupOrCourseMember().getId();
            await().atMost(Duration.ofSeconds(60)).until(
                    () -> distributedDataAccessService.getQueuedJobsForCourse(courseId).isEmpty() && distributedDataAccessService.getProcessingJobsForCourse(courseId).isEmpty());
        }
        deleteDemoRepositories();
    }

    /**
     * All in one test, because the demo programming exercise is created only once: its fixed project key would collide with the repositories of a second one, and a test that
     * finds it already created could not see its creation.
     */
    @Test
    void seedsProgrammingExerciseThatStudentsCanStartRightAway() throws IOException {
        DemoSeeding.seed(demoDataSeedingService);

        ProgrammingExercise exercise = demoProgrammingExercise().orElseThrow();
        assertThat(exercise.isVisibleToStudents()).as("the demo programming exercise is released").isTrue();
        assertThat(exercise.getDueDate()).as("the demo programming exercise is still open for submissions").isAfter(ZonedDateTime.now());
        assertThat(exercise.getProblemStatement()).as("the demo programming exercise ships the assignment of its template")
                .isEqualTo(resourceLoaderService.getResource(Path.of("templates", "java", "plain_maven", "readme")).getContentAsString(StandardCharsets.UTF_8));

        ProgrammingExercise withParticipations = programmingExerciseRepository.findWithAllParticipationsById(exercise.getId()).orElseThrow();
        localVCLocalCITestService.verifyRepositoryFoldersExist(withParticipations, localVCBasePath);
        // Setting up the exercise triggers the first builds of the template and the solution repository, which queues a build job for each of them.
        assertThat(buildJobRepository.findAll()).extracting(BuildJob::getParticipationId).as("the builds of the template and the solution repository were triggered")
                .contains(withParticipations.getTemplateParticipation().getId(), withParticipations.getSolutionParticipation().getId());

        // Like the other exercises of the course, the programming exercise is part of the lecture and the competency of its topic.
        Course course = exercise.getCourseViaExerciseGroupOrCourseMember();
        assertThat(lectureRepository.findAllByTitleAndCourseIdWithLectureUnits("Algorithms and Complexity", course.getId())).singleElement()
                .satisfies(lecture -> assertThat(lecture.getLectureUnits()).as("the algorithms lecture links the programming exercise")
                        .anyMatch(unit -> unit instanceof ExerciseUnit exerciseUnit && exercise.getId().equals(exerciseUnit.getExercise().getId())));
        assertThat(competencyExerciseLinkRepository.findByExerciseIdWithCompetency(exercise.getId()))
                .extracting(link -> link.getCompetency().getTitle(), CompetencyExerciseLink::getWeight).as("the programming exercise counts towards the algorithms competency")
                .containsExactly(tuple("Analyze algorithm complexity", 1.0));

        DemoSeeding.seed(demoDataSeedingService);

        assertThat(demoProgrammingExercise()).as("an existing demo programming exercise is neither duplicated nor replaced").map(ProgrammingExercise::getId)
                .contains(exercise.getId());
        assertThat(programmingExerciseRepository.countByShortNameAndCourse(ProgrammingDemoApi.DEMO_EXERCISE_SHORT_NAME, course)).isOne();
    }

    private Optional<ProgrammingExercise> demoProgrammingExercise() {
        return courseRepository.findAllByShortName(CourseDemoApi.DEMO_COURSE_SHORT_NAME).stream().findFirst()
                .flatMap(course -> programmingExerciseRepository.findByShortNameAndCourseIdWithCompetencies(ProgrammingDemoApi.DEMO_EXERCISE_SHORT_NAME, course.getId()));
    }

    private void deleteDemoRepositories() throws IOException {
        RepositoryExportTestUtil.deleteLocalVcProjectIfPresent(localVCBasePath, DEMO_PROJECT_KEY);
        // The server only copies the template into a repository whose working copy is empty, so stale working copies would leave the recreated repositories without commits.
        FileUtils.deleteDirectory(repoClonePath.resolve(DEMO_PROJECT_KEY).toFile());
    }
}
