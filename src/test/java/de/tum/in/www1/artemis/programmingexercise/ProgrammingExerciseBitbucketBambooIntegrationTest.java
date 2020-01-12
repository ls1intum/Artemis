package de.tum.in.www1.artemis.programmingexercise;

import static de.tum.in.www1.artemis.web.rest.ProgrammingExerciseResource.Endpoints.ROOT;
import static de.tum.in.www1.artemis.web.rest.ProgrammingExerciseResource.Endpoints.SETUP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.ZonedDateTime;
import java.util.LinkedList;
import java.util.List;

import org.eclipse.jgit.api.errors.GitAPIException;
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
import de.tum.in.www1.artemis.domain.File;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.enumeration.InitializationState;
import de.tum.in.www1.artemis.domain.enumeration.RepositoryType;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.util.RequestUtilService;
import de.tum.in.www1.artemis.util.Verifiable;
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

    private Course course;

    private ProgrammingExercise exercise;

    @BeforeEach
    public void setup() {
        database.addUsers(1, 1, 1);
        course = database.addCourseWithOneProgrammingExerciseAndTestCases();
        exercise = programmingExerciseRepository.findAll().get(0);
        exercise.setReleaseDate(ZonedDateTime.now().minusDays(1));
        programmingExerciseRepository.save(exercise);
        bambooRequestMockProvider.enableMockingOfRequests();
        bitbucketRequestMockProvider.enableMockingOfRequests();
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
        reset(gitService);
        reset(bambooServer);
        bitbucketRequestMockProvider.reset();
        bambooRequestMockProvider.reset();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void setupProgrammingExercise_validExercise_created() throws Exception {
        final var exercise = ModelFactory.generateProgrammingExercise(ZonedDateTime.now().plusDays(1), ZonedDateTime.now().plusDays(7), course);
        mockConnectorRequestsForSetup(exercise);

        final var generated = request.postWithResponseBody(ROOT + SETUP, exercise, ProgrammingExercise.class, HttpStatus.CREATED);

        exercise.setId(generated.getId());
        assertThat(exercise).isEqualTo(generated);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void startProgrammingExercise_student_correctInitializationState() throws Exception {
        final var course = exercise.getCourse();
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

        // TODO mock the actual git calls in the git service
        doNothing().when(continuousIntegrationService).performEmptySetupCommit(any());

        return verifications;
    }

    private void mockConnectorRequestsForSetup(ProgrammingExercise exercise) throws IOException, URISyntaxException, GitAPIException, InterruptedException {
        final var projectKey = exercise.getProjectKey();
        String exerciseRepoName = projectKey.toLowerCase() + "-" + RepositoryType.TEMPLATE.getName();
        String testRepoName = projectKey.toLowerCase() + "-" + RepositoryType.TESTS.getName();
        String solutionRepoName = projectKey.toLowerCase() + "-" + RepositoryType.SOLUTION.getName();
        bambooRequestMockProvider.mockCheckIfProjectExists(exercise);
        bitbucketRequestMockProvider.mockCheckIfProjectExists(exercise);
        bitbucketRequestMockProvider.mockCreateProjectForExercise(exercise);
        bitbucketRequestMockProvider.mockCreateRepository(exercise, exerciseRepoName);
        bitbucketRequestMockProvider.mockCreateRepository(exercise, testRepoName);
        bitbucketRequestMockProvider.mockCreateRepository(exercise, solutionRepoName);
        bitbucketRequestMockProvider.mockAddWebHooks(exercise);
        bambooRequestMockProvider.mockRemoveAllDefaultProjectPermissions(exercise);
        bambooRequestMockProvider.mockGiveProjectPermissions(exercise);

        // TODO: don't mock the git and file services, but actually provide some fake repositories on a lower level in order
        // to also properly test these methods
        doReturn(null).when(gitService).getOrCheckoutRepository(any(URL.class), anyBoolean());
        doReturn(List.of(new File(java.io.File.createTempFile("artemis", "test"), null))).when(gitService).listFiles(any());
        doNothing().when(gitService).commitAndPush(any(), anyString(), any());

        // TODO: check the actual plan and plan permissions that get passed here
        doReturn(null).when(bambooServer).publish(any());
    }
}
