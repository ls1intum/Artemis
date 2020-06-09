package de.tum.in.www1.artemis.programmingexercise;

import static de.tum.in.www1.artemis.web.rest.ProgrammingExerciseResource.Endpoints.ROOT;
import static de.tum.in.www1.artemis.web.rest.ProgrammingExerciseResource.Endpoints.SETUP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationJenkinsGitlabTest;
import de.tum.in.www1.artemis.connector.gitlab.GitlabRequestMockProvider;
import de.tum.in.www1.artemis.connector.jenkins.JenkinsRequestMockProvider;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseMode;
import de.tum.in.www1.artemis.domain.enumeration.InitializationState;
import de.tum.in.www1.artemis.domain.enumeration.RepositoryType;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.util.*;
import de.tum.in.www1.artemis.web.rest.ParticipationResource;

class ProgrammingExerciseGitlabJenkinsIntegrationTest extends AbstractSpringIntegrationJenkinsGitlabTest {

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

    @Autowired
    private UserRepository userRepo;

    private ProgrammingExercise exercise;

    private final static int numberOfStudents = 2;

    private final static String studentLogin = "student1";

    private final static String teamShortName = "team1";

    private LocalRepository exerciseRepo = new LocalRepository();

    private LocalRepository testRepo = new LocalRepository();

    private LocalRepository solutionRepo = new LocalRepository();

    private LocalRepository studentRepo = new LocalRepository();

    private LocalRepository studentTeamRepo = new LocalRepository();

    @BeforeEach
    void setup() throws Exception {
        database.addUsers(numberOfStudents, 1, 1);
        Course course = database.addEmptyCourse();
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

        doReturn(exerciseRepoName).when(versionControlService).getRepositorySlugFromUrl(exerciseRepoTestUrl.getURL());
        doReturn(testRepoName).when(versionControlService).getRepositorySlugFromUrl(testRepoTestUrl.getURL());
        doReturn(solutionRepoName).when(versionControlService).getRepositorySlugFromUrl(solutionRepoTestUrl.getURL());
        doReturn(studentRepoName).when(versionControlService).getRepositorySlugFromUrl(studentRepoTestUrl.getURL());
        doReturn(studentTeamRepoName).when(versionControlService).getRepositorySlugFromUrl(studentTeamRepoTestUrl.getURL());
    }

    @AfterEach
    void tearDown() throws IOException {
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
    void setupProgrammingExercise_validExercise_created(ExerciseMode mode) throws Exception {
        User instructor = userRepo.findOneByLogin("instructor1").orElseThrow();
        User tutor = userRepo.findOneByLogin("tutor1").orElseThrow();
        exercise.setMode(mode);
        mockConnectorRequestsForSetup(exercise, List.of(instructor, tutor));
        final var generatedExercise = request.postWithResponseBody(ROOT + SETUP, exercise, ProgrammingExercise.class, HttpStatus.CREATED);

        exercise.setId(generatedExercise.getId());
        assertThat(exercise).isEqualTo(generatedExercise);
        assertThat(programmingExerciseRepository.count()).isEqualTo(1);
    }

    @Test
    @WithMockUser(username = studentLogin, roles = "USER")
    public void startProgrammingExercise_student_correctInitializationState() throws Exception {
        final var course = exercise.getCourse();
        programmingExerciseRepository.save(exercise);
        database.addTemplateParticipationForProgrammingExercise(exercise);
        database.addSolutionParticipationForProgrammingExercise(exercise);

        User user = userRepo.findOneByLogin(studentLogin).orElseThrow();
        final var verifications = mockConnectorRequestsForStartParticipation(exercise, user.getParticipantIdentifier(), List.of(user));
        final var path = ParticipationResource.Endpoints.ROOT
                + ParticipationResource.Endpoints.START_PARTICIPATION.replace("{courseId}", "" + course.getId()).replace("{exerciseId}", "" + exercise.getId());
        final var participation = request.postWithResponseBody(path, null, ProgrammingExerciseStudentParticipation.class, HttpStatus.CREATED);

        for (final var verification : verifications) {
            verification.performVerification();
        }

        assertThat(participation.getInitializationState()).as("Participation should be initialized").isEqualTo(InitializationState.INITIALIZED);
    }

    private List<Verifiable> mockConnectorRequestsForStartParticipation(ProgrammingExercise exercise, String username, List<User> users) throws Exception {
        final var verifications = new LinkedList<Verifiable>();
        gitlabRequestMockProvider.setupMockUsers(users);
        gitlabRequestMockProvider.setupMockMembers(users);
        gitlabRequestMockProvider.mockCopyRepositoryForParticipation(exercise, username);
        gitlabRequestMockProvider.mockConfigureRepository(exercise, username);
        jenkinsRequestMockProvider.mockCopyBuildPlanForParticipation(exercise, username);
        jenkinsRequestMockProvider.mockUpdatePlanRepositoryForParticipation(exercise, username);
        gitlabRequestMockProvider.mockAddAuthenticatedWebHook();
        return verifications;
    }

    private void mockConnectorRequestsForSetup(ProgrammingExercise exercise, List<User> users) throws Exception {
        final var projectKey = exercise.getProjectKey();
        String exerciseRepoName = projectKey.toLowerCase() + "-" + RepositoryType.TEMPLATE.getName();
        String testRepoName = projectKey.toLowerCase() + "-" + RepositoryType.TESTS.getName();
        String solutionRepoName = projectKey.toLowerCase() + "-" + RepositoryType.SOLUTION.getName();
        gitlabRequestMockProvider.setupMockUsers(users);
        gitlabRequestMockProvider.setupMockMembers(users);
        jenkinsRequestMockProvider.mockCheckIfProjectExists(exercise, false);
        gitlabRequestMockProvider.mockCheckIfProjectExists(exercise, false);
        gitlabRequestMockProvider.mockCreateProjectForExercise(exercise);
        gitlabRequestMockProvider.mockCreateRepository(exercise, exerciseRepoName);
        gitlabRequestMockProvider.mockCreateRepository(exercise, testRepoName);
        gitlabRequestMockProvider.mockCreateRepository(exercise, solutionRepoName);
        gitlabRequestMockProvider.mockAddAuthenticatedWebHook();
        jenkinsRequestMockProvider.mockCreateProjectForExercise(exercise);
        jenkinsRequestMockProvider.mockCreateBuildPlan(exercise);
        jenkinsRequestMockProvider.mockTriggerBuild();
    }
}
