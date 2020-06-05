package de.tum.in.www1.artemis.programmingexercise;

import de.tum.in.www1.artemis.AbstractSpringIntegrationJenkinsGitlabTest;
import de.tum.in.www1.artemis.connector.gitlab.GitlabRequestMockProvider;
import de.tum.in.www1.artemis.connector.jenkins.JenkinsRequestMockProvider;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseMode;
import de.tum.in.www1.artemis.domain.enumeration.RepositoryType;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.util.*;
import org.gitlab4j.api.GitLabApiException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import static de.tum.in.www1.artemis.web.rest.ProgrammingExerciseResource.Endpoints.ROOT;
import static de.tum.in.www1.artemis.web.rest.ProgrammingExerciseResource.Endpoints.SETUP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class ProgrammingExerciseGitlabJenkinsIntegrationTest extends AbstractSpringIntegrationJenkinsGitlabTest {
    @Autowired
    private DatabaseUtilService database;

    @Autowired
    private RequestUtilService request;

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    private JenkinsRequestMockProvider jenkinsRequestMockProvider;

    @Autowired
    private GitlabRequestMockProvider gitlabRequestMockProvider;

    private Course course;

    private ProgrammingExercise exercise;

    private final static int numberOfStudents = 2;

    private final static String studentLogin = "student1";

    private final static String teamShortName = "team1";

    LocalRepository exerciseRepo = new LocalRepository();

    LocalRepository testRepo = new LocalRepository();

    LocalRepository solutionRepo = new LocalRepository();

    LocalRepository studentRepo = new LocalRepository();

    LocalRepository studentTeamRepo = new LocalRepository();

    @BeforeEach
    public void setup() throws Exception {
        database.addUsers(numberOfStudents, 1, 1);
        course = database.addEmptyCourse();
        exercise = ModelFactory.generateProgrammingExercise(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(7), course);
        jenkinsRequestMockProvider.enableMockingOfRequests(jenkinsServer);
        gitlabRequestMockProvider.enableMockingOfRequests();

        exerciseRepo.configureRepos("exerciseLocalRepo", "exerciseOriginRepo");
        testRepo.configureRepos("testLocalRepo", "testOriginRepo");
        solutionRepo.configureRepos("solutionLocalRepo", "solutionOriginRepo");
        studentRepo.configureRepos("studentRepo", "studentOriginRepo");
        studentTeamRepo.configureRepos("studentTeamRepo", "studentTeamOriginRepo");

        final var projectKey = exercise.getProjectKey();
        String exerciseRepoName = projectKey.toLowerCase() + "-" + RepositoryType.TEMPLATE.getName();
        String testRepoName = projectKey.toLowerCase() + "-" + RepositoryType.TESTS.getName();
        String solutionRepoName = projectKey.toLowerCase() + "-" + RepositoryType.SOLUTION.getName();
        String studentRepoName = projectKey.toLowerCase() + "-" + studentLogin;
        String studentTeamRepoName = projectKey.toLowerCase() + "-" + teamShortName;

        var exerciseRepoTestUrl = new GitUtilService.MockFileRepositoryUrl(exerciseRepo.originRepoFile);
        var testRepoTestUrl = new GitUtilService.MockFileRepositoryUrl(testRepo.originRepoFile);
        var solutionRepoTestUrl = new GitUtilService.MockFileRepositoryUrl(solutionRepo.originRepoFile);
        var studentRepoTestUrl = new GitUtilService.MockFileRepositoryUrl(studentRepo.originRepoFile);
        var studentTeamRepoTestUrl = new GitUtilService.MockFileRepositoryUrl(studentTeamRepo.originRepoFile);

        doReturn(exerciseRepoTestUrl).when(versionControlService).getCloneRepositoryUrl(projectKey, exerciseRepoName);
        doReturn(testRepoTestUrl).when(versionControlService).getCloneRepositoryUrl(projectKey, testRepoName);
        doReturn(solutionRepoTestUrl).when(versionControlService).getCloneRepositoryUrl(projectKey, solutionRepoName);
        doReturn(studentRepoTestUrl).when(versionControlService).getCloneRepositoryUrl(projectKey, studentRepoName);
        doReturn(studentTeamRepoTestUrl).when(versionControlService).getCloneRepositoryUrl(projectKey, studentTeamRepoName);

        doReturn(gitService.getRepositoryByLocalPath(exerciseRepo.localRepoFile.toPath())).when(gitService).getOrCheckoutRepository(exerciseRepoTestUrl.getURL(), true);
        doReturn(gitService.getRepositoryByLocalPath(testRepo.localRepoFile.toPath())).when(gitService).getOrCheckoutRepository(testRepoTestUrl.getURL(), true);
        doReturn(gitService.getRepositoryByLocalPath(solutionRepo.localRepoFile.toPath())).when(gitService).getOrCheckoutRepository(solutionRepoTestUrl.getURL(), true);
        doReturn(gitService.getRepositoryByLocalPath(studentRepo.localRepoFile.toPath())).when(gitService).getOrCheckoutRepository(studentRepoTestUrl.getURL(), true);
        doReturn(gitService.getRepositoryByLocalPath(studentTeamRepo.localRepoFile.toPath())).when(gitService).getOrCheckoutRepository(studentTeamRepoTestUrl.getURL(), true);

        doReturn(exerciseRepoName).when(continuousIntegrationService).getRepositorySlugFromUrl(exerciseRepoTestUrl.getURL());
        doReturn(testRepoName).when(continuousIntegrationService).getRepositorySlugFromUrl(testRepoTestUrl.getURL());
        doReturn(solutionRepoName).when(continuousIntegrationService).getRepositorySlugFromUrl(solutionRepoTestUrl.getURL());
        doReturn(studentRepoName).when(continuousIntegrationService).getRepositorySlugFromUrl(studentRepoTestUrl.getURL());
        doReturn(studentTeamRepoName).when(continuousIntegrationService).getRepositorySlugFromUrl(studentTeamRepoTestUrl.getURL());

        doReturn(exerciseRepoName).when(versionControlService).getRepositorySlugFromUrl(exerciseRepoTestUrl.getURL());
        doReturn(testRepoName).when(versionControlService).getRepositorySlugFromUrl(testRepoTestUrl.getURL());
        doReturn(solutionRepoName).when(versionControlService).getRepositorySlugFromUrl(solutionRepoTestUrl.getURL());
        doReturn(studentRepoName).when(versionControlService).getRepositorySlugFromUrl(studentRepoTestUrl.getURL());
        doReturn(studentTeamRepoName).when(versionControlService).getRepositorySlugFromUrl(studentTeamRepoTestUrl.getURL());

        doReturn(projectKey).when(versionControlService).getProjectKeyFromUrl(any());
    }

    @AfterEach
    public void tearDown() throws IOException {
        database.resetDatabase();
        reset(gitService);
        gitlabRequestMockProvider.reset();
        jenkinsRequestMockProvider.reset();
        exerciseRepo.resetLocalRepo();
        testRepo.resetLocalRepo();
        solutionRepo.resetLocalRepo();
        studentRepo.resetLocalRepo();
        studentTeamRepo.resetLocalRepo();
    }

    @ParameterizedTest
    @EnumSource(ExerciseMode.class)
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void setupProgrammingExercise_validExercise_created(ExerciseMode mode) throws Exception {
        exercise.setMode(mode);
        final var verifications = mockConnectorRequestsForSetup(exercise);
        final var generatedExercise = request.postWithResponseBody(ROOT + SETUP, exercise, ProgrammingExercise.class, HttpStatus.CREATED);

        exercise.setId(generatedExercise.getId());
        for (final var verification: verifications) {
            verification.performVerification();
        }
        assertThat(exercise).isEqualTo(generatedExercise);
        assertThat(programmingExerciseRepository.count()).isEqualTo(1);
    }

    private List<Verifiable> mockConnectorRequestsForSetup(ProgrammingExercise exercise) throws IOException, GitLabApiException {
        List<Verifiable> results = new ArrayList<>();
        final var projectKey = exercise.getProjectKey();
        String exerciseRepoName = projectKey.toLowerCase() + "-" + RepositoryType.TEMPLATE.getName();
        String testRepoName = projectKey.toLowerCase() + "-" + RepositoryType.TESTS.getName();
        String solutionRepoName = projectKey.toLowerCase() + "-" + RepositoryType.SOLUTION.getName();
        jenkinsRequestMockProvider.mockCheckIfProjectExists(exercise, false);
        gitlabRequestMockProvider.mockCheckIfProjectExists(exercise, false);
        gitlabRequestMockProvider.mockCreateProjectForExercise(exercise);
        gitlabRequestMockProvider.mockCreateRepository(exercise, exerciseRepoName);
        gitlabRequestMockProvider.mockCreateRepository(exercise, testRepoName);
        gitlabRequestMockProvider.mockCreateRepository(exercise, solutionRepoName);
        gitlabRequestMockProvider.addAuthenticatedWebHook();
        jenkinsRequestMockProvider.mockCreateProjectForExercise(exercise);
        jenkinsRequestMockProvider.mockCreateBuildPlanForExercise(exercise);
        jenkinsRequestMockProvider.mockTriggerBuild();
        jenkinsRequestMockProvider.mockRemoveAllDefaultProjectPermissions(exercise);
        return results;
    }
}
