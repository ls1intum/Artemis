package de.tum.in.www1.artemis.programmingexercise;

import static de.tum.in.www1.artemis.web.rest.ProgrammingExerciseResource.Endpoints.ROOT;
import static de.tum.in.www1.artemis.web.rest.ProgrammingExerciseResource.Endpoints.SETUP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.time.ZonedDateTime;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.StoredConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationTest;
import de.tum.in.www1.artemis.connector.bamboo.BambooRequestMockProvider;
import de.tum.in.www1.artemis.connector.bitbucket.BitbucketRequestMockProvider;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.enumeration.InitializationState;
import de.tum.in.www1.artemis.domain.enumeration.RepositoryType;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.util.*;
import de.tum.in.www1.artemis.web.rest.ParticipationResource;

public class ProgrammingExerciseBitbucketBambooIntegrationTest extends AbstractSpringIntegrationTest {

    @Autowired
    private DatabaseUtilService database;

    @Autowired
    private RequestUtilService request;

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    private BambooRequestMockProvider bambooRequestMockProvider;

    @Autowired
    private BitbucketRequestMockProvider bitbucketRequestMockProvider;

    @Autowired
    private CourseRepository courseRepository;

    private Course course;

    private ProgrammingExercise exercise;

    public class LocalRepo {

        File localRepoFile;

        File originRepoFile;

        Git localGit;

        Git originGit;

        void configureRepos(String localRepoFileName, String originRepoFileName) throws Exception {

            this.localRepoFile = Files.createTempDirectory(localRepoFileName).toFile();
            this.localGit = Git.init().setDirectory(localRepoFile).call();

            this.originRepoFile = Files.createTempDirectory(originRepoFileName).toFile();
            this.originGit = Git.init().setDirectory(originRepoFile).call();

            StoredConfig config = this.localGit.getRepository().getConfig();
            config.setString("remote", "origin", "url", this.originRepoFile.getAbsolutePath());
            config.save();
        }
    }

    LocalRepo exerciseRepo = new LocalRepo();

    LocalRepo testRepo = new LocalRepo();

    LocalRepo solutionRepo = new LocalRepo();

    LocalRepo studentRepo = new LocalRepo();

    @BeforeEach
    public void setup() throws Exception {
        database.addUsers(1, 1, 1);
        course = database.addEmptyCourse();
        exercise = ModelFactory.generateProgrammingExercise(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(7), course);
        bambooRequestMockProvider.enableMockingOfRequests();
        bitbucketRequestMockProvider.enableMockingOfRequests();

        exerciseRepo.configureRepos("exerciseLocalRepo", "exerciseOriginRepo");
        testRepo.configureRepos("testLocalRepo", "testOriginRepo");
        solutionRepo.configureRepos("solutionLocalRepo", "solutionOriginRepo");
        studentRepo.configureRepos("studentRepo", "studentOriginaRepo");

        final var projectKey = exercise.getProjectKey();
        String exerciseRepoName = projectKey.toLowerCase() + "-" + RepositoryType.TEMPLATE.getName();
        String testRepoName = projectKey.toLowerCase() + "-" + RepositoryType.TESTS.getName();
        String solutionRepoName = projectKey.toLowerCase() + "-" + RepositoryType.SOLUTION.getName();
        String studentRepoName = projectKey.toLowerCase() + "-" + "student1";

        var exerciseRepoTestUrl = new GitUtilService.MockFileRepositoryUrl(exerciseRepo.originRepoFile);
        var testRepoTestUrl = new GitUtilService.MockFileRepositoryUrl(testRepo.originRepoFile);
        var solutionRepoTestUrl = new GitUtilService.MockFileRepositoryUrl(solutionRepo.originRepoFile);
        var studentRepoTestUrl = new GitUtilService.MockFileRepositoryUrl(studentRepo.originRepoFile);

        doReturn(exerciseRepoTestUrl).when(versionControlService).getCloneRepositoryUrl(projectKey, exerciseRepoName);
        doReturn(testRepoTestUrl).when(versionControlService).getCloneRepositoryUrl(projectKey, testRepoName);
        doReturn(solutionRepoTestUrl).when(versionControlService).getCloneRepositoryUrl(projectKey, solutionRepoName);
        doReturn(studentRepoTestUrl).when(versionControlService).getCloneRepositoryUrl(projectKey, studentRepoName);

        doReturn(gitService.getRepositoryByLocalPath(exerciseRepo.localRepoFile.toPath())).when(gitService).getOrCheckoutRepository(exerciseRepoTestUrl.getURL(), true);
        doReturn(gitService.getRepositoryByLocalPath(testRepo.localRepoFile.toPath())).when(gitService).getOrCheckoutRepository(testRepoTestUrl.getURL(), true);
        doReturn(gitService.getRepositoryByLocalPath(solutionRepo.localRepoFile.toPath())).when(gitService).getOrCheckoutRepository(solutionRepoTestUrl.getURL(), true);
        doReturn(gitService.getRepositoryByLocalPath(studentRepo.localRepoFile.toPath())).when(gitService).getOrCheckoutRepository(studentRepoTestUrl.getURL(), true);

        doReturn(exerciseRepoName).when(continuousIntegrationService).getRepositorySlugFromUrl(exerciseRepoTestUrl.getURL());
        doReturn(testRepoName).when(continuousIntegrationService).getRepositorySlugFromUrl(testRepoTestUrl.getURL());
        doReturn(solutionRepoName).when(continuousIntegrationService).getRepositorySlugFromUrl(solutionRepoTestUrl.getURL());
        doReturn(studentRepoName).when(continuousIntegrationService).getRepositorySlugFromUrl(studentRepoTestUrl.getURL());

        doReturn(exerciseRepoName).when(versionControlService).getRepositorySlugFromUrl(exerciseRepoTestUrl.getURL());
        doReturn(testRepoName).when(versionControlService).getRepositorySlugFromUrl(testRepoTestUrl.getURL());
        doReturn(solutionRepoName).when(versionControlService).getRepositorySlugFromUrl(solutionRepoTestUrl.getURL());
        doReturn(studentRepoName).when(versionControlService).getRepositorySlugFromUrl(studentRepoTestUrl.getURL());

        doReturn(projectKey).when(versionControlService).getProjectKeyFromUrl(any());
    }

    @AfterEach
    public void tearDown() throws IOException {
        database.resetDatabase();
        reset(gitService);
        reset(bambooServer);
        bitbucketRequestMockProvider.reset();
        bambooRequestMockProvider.reset();
        resetLocalRepo(exerciseRepo);
        resetLocalRepo(testRepo);
        resetLocalRepo(solutionRepo);
    }

    private void resetLocalRepo(LocalRepo localRepo) throws IOException {
        if (localRepo.localRepoFile != null && localRepo.localRepoFile.exists()) {
            FileUtils.deleteDirectory(localRepo.localRepoFile);
        }
        if (localRepo.localGit != null) {
            localRepo.localGit.close();
        }

        if (localRepo.originRepoFile != null && localRepo.originRepoFile.exists()) {
            FileUtils.deleteDirectory(localRepo.originRepoFile);
        }
        if (localRepo.originGit != null) {
            localRepo.originGit.close();
        }
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void setupProgrammingExercise_validExercise_created() throws Exception {
        mockConnectorRequestsForSetup(exercise);
        final var generatedExercise = request.postWithResponseBody(ROOT + SETUP, exercise, ProgrammingExercise.class, HttpStatus.CREATED);

        exercise.setId(generatedExercise.getId());
        assertThat(exercise).isEqualTo(generatedExercise);
        assertThat(programmingExerciseRepository.count()).isEqualTo(1);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void setupProgrammingExercise_noTutors_created() throws Exception {
        course.setTeachingAssistantGroupName(null);
        courseRepository.save(course);
        mockConnectorRequestsForSetup(exercise);

        final var generatedExercise = request.postWithResponseBody(ROOT + SETUP, exercise, ProgrammingExercise.class, HttpStatus.CREATED);

        exercise.setId(generatedExercise.getId());
        assertThat(exercise).isEqualTo(generatedExercise);
        assertThat(programmingExerciseRepository.count()).isEqualTo(1);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void startProgrammingExercise_student_correctInitializationState() throws Exception {
        final var course = exercise.getCourse();
        programmingExerciseRepository.save(exercise);
        database.addTemplateParticipationForProgrammingExercise(exercise);
        database.addSolutionParticipationForProgrammingExercise(exercise);
        final var verifications = mockConnectorRequestsForStartParticipation(exercise, "student1");

        final var path = ParticipationResource.Endpoints.ROOT
                + ParticipationResource.Endpoints.START_PARTICIPATION.replace("{courseId}", "" + course.getId()).replace("{exerciseId}", "" + exercise.getId());
        final var participation = request.postWithResponseBody(path, null, ProgrammingExerciseStudentParticipation.class, HttpStatus.CREATED);

        for (final var verification : verifications) {
            verification.performVerification();
        }

        assertThat(participation.getInitializationState()).as("Participation should be initialized").isEqualTo(InitializationState.INITIALIZED);
    }

    private List<Verifiable> mockConnectorRequestsForStartParticipation(ProgrammingExercise exercise, String username) throws Exception {
        final var verifications = new LinkedList<Verifiable>();
        bitbucketRequestMockProvider.mockCopyRepositoryForParticipation(exercise, username);
        bitbucketRequestMockProvider.mockConfigureRepository(exercise, username);
        verifications.addAll(bambooRequestMockProvider.mockCopyBuildPlanForParticipation(exercise, username));
        verifications.addAll(bambooRequestMockProvider.mockUpdatePlanRepositoryForParticipation(exercise, username));
        bitbucketRequestMockProvider.mockAddWebHooks(exercise);
        return verifications;
    }

    private void mockConnectorRequestsForSetup(ProgrammingExercise exercise) throws IOException, URISyntaxException, GitAPIException, InterruptedException {
        final var projectKey = exercise.getProjectKey();
        String exerciseRepoName = projectKey.toLowerCase() + "-" + RepositoryType.TEMPLATE.getName();
        String testRepoName = projectKey.toLowerCase() + "-" + RepositoryType.TESTS.getName();
        String solutionRepoName = projectKey.toLowerCase() + "-" + RepositoryType.SOLUTION.getName();
        bambooRequestMockProvider.mockCheckIfProjectExists(exercise, false);
        bitbucketRequestMockProvider.mockCheckIfProjectExists(exercise, false);
        bitbucketRequestMockProvider.mockCreateProjectForExercise(exercise);
        bitbucketRequestMockProvider.mockCreateRepository(exercise, exerciseRepoName);
        bitbucketRequestMockProvider.mockCreateRepository(exercise, testRepoName);
        bitbucketRequestMockProvider.mockCreateRepository(exercise, solutionRepoName);
        bitbucketRequestMockProvider.mockAddWebHooks(exercise);
        bambooRequestMockProvider.mockRemoveAllDefaultProjectPermissions(exercise);
        bambooRequestMockProvider.mockGiveProjectPermissions(exercise);

        // TODO: check the actual plan and plan permissions that get passed here
        doReturn(null).when(bambooServer).publish(any());
    }
}
