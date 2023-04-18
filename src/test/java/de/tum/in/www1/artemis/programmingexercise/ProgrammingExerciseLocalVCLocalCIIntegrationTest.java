package de.tum.in.www1.artemis.programmingexercise;

import static de.tum.in.www1.artemis.web.rest.ProgrammingExerciseResourceEndpoints.PROGRAMMING_EXERCISES;
import static de.tum.in.www1.artemis.web.rest.ProgrammingExerciseResourceEndpoints.ROOT;
import static de.tum.in.www1.artemis.web.rest.ProgrammingExerciseResourceEndpoints.SETUP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.in.www1.artemis.AbstractSpringIntegrationLocalCILocalVCTest;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.enumeration.ProjectType;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.participation.SolutionProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.TemplateProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.service.connectors.localvc.LocalVCRepositoryUrl;
import de.tum.in.www1.artemis.util.LocalRepository;
import de.tum.in.www1.artemis.util.ModelFactory;

public class ProgrammingExerciseLocalVCLocalCIIntegrationTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "localvclocalciprogex";

    @BeforeEach
    void setup() throws Exception {
        database.addUsers(TEST_PREFIX, 1, 1, 0, 1);

        // The mock commit hashes don't allow the getPushDate() method in the LocalVCService to retrieve the push date using the commit hash. Thus, this method must be mocked.
        doReturn(ZonedDateTime.now().minusSeconds(2)).when(versionControlService).getPushDate(any(), any(), any());

        mockDockerClientMethods();

        // Mock dockerClient.copyArchiveFromContainerCmd() such that it returns a dummy commitHash for both the template and the solution repository.
        // Note: The stub needs to receive the same object twice. Usually, specifying one doReturn() is enough to make the stub return the same object on every subsequent call.
        // However, in this case we have it return an InputStream, which will be consumed after returning it the first time, so we need to create two separate ones.
        localVCLocalCITestService.mockInputStreamReturnedFromContainer(dockerClient, "/repositories/assignment-repository/.git/refs/heads/[^/]+",
                Map.of("testCommitHash", DUMMY_COMMIT_HASH), Map.of("testCommitHash", DUMMY_COMMIT_HASH));
        localVCLocalCITestService.mockInputStreamReturnedFromContainer(dockerClient, "/repositories/test-repository/.git/refs/heads/[^/]+",
                Map.of("testCommitHash", DUMMY_COMMIT_HASH), Map.of("testCommitHash", DUMMY_COMMIT_HASH));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateProgrammingExercise() throws Exception {
        Course course = database.addEmptyCourse();
        ProgrammingExercise exercise = ModelFactory.generateProgrammingExercise(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(7), course);
        exercise.setProjectType(ProjectType.PLAIN_GRADLE);

        // Mock dockerClient.copyArchiveFromContainerCmd() such that it returns the XMLs containing the test results.
        // Mock the results for the template repository build and for the solution repository build that will both be triggered as a result of creating the exercise.
        Map<String, String> templateBuildTestResults = localVCLocalCITestService.createMapFromTestResultsFolder(ALL_FAIL_TEST_RESULTS_PATH);
        Map<String, String> solutionBuildTestResults = localVCLocalCITestService.createMapFromTestResultsFolder(ALL_SUCCEED_TEST_RESULTS_PATH);
        localVCLocalCITestService.mockInputStreamReturnedFromContainer(dockerClient, "/repositories/test-repository/build/test-results/test", templateBuildTestResults,
                solutionBuildTestResults);

        ProgrammingExercise createdExercise = request.postWithResponseBody(ROOT + SETUP, exercise, ProgrammingExercise.class, HttpStatus.CREATED);

        // Check that the repository folders were created in the file system for the template, solution, and tests repository.
        LocalVCRepositoryUrl templateRepositoryUrl = new LocalVCRepositoryUrl(createdExercise.getTemplateRepositoryUrl(), localVCBaseUrl);
        assertThat(Files.exists(templateRepositoryUrl.getLocalRepositoryPath(localVCBasePath))).isTrue();
        LocalVCRepositoryUrl solutionRepositoryUrl = new LocalVCRepositoryUrl(createdExercise.getSolutionRepositoryUrl(), localVCBaseUrl);
        assertThat(Files.exists(solutionRepositoryUrl.getLocalRepositoryPath(localVCBasePath))).isTrue();
        LocalVCRepositoryUrl testsRepositoryUrl = new LocalVCRepositoryUrl(createdExercise.getTestRepositoryUrl(), localVCBaseUrl);
        assertThat(Files.exists(testsRepositoryUrl.getLocalRepositoryPath(localVCBasePath))).isTrue();

        // Also check that the template and solution repositories were built successfully.
        localVCLocalCITestService.testLastestSubmission(createdExercise.getTemplateParticipation().getId(), null, 0, false);
        localVCLocalCITestService.testLastestSubmission(createdExercise.getSolutionParticipation().getId(), null, 13, false);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteProgrammingExercise() throws Exception {
        Course course = database.addCourseWithOneProgrammingExercise();
        ProgrammingExercise exercise = database.getFirstExerciseWithType(course, ProgrammingExercise.class);
        String projectKey = exercise.getProjectKey();
        exercise.setTestRepositoryUrl(localVCBaseUrl + "/git/" + projectKey + "/" + projectKey.toLowerCase() + "-tests.git");
        programmingExerciseRepository.save(exercise);
        exercise = programmingExerciseRepository.findWithAllParticipationsById(exercise.getId()).orElseThrow();

        // Set the correct repository URLs for the template and the solution participation.
        String templateRepositorySlug = projectKey.toLowerCase() + "-exercise";
        TemplateProgrammingExerciseParticipation templateParticipation = exercise.getTemplateParticipation();
        templateParticipation.setRepositoryUrl(localVCBaseUrl + "/git/" + projectKey + "/" + templateRepositorySlug + ".git");
        templateProgrammingExerciseParticipationRepository.save(templateParticipation);
        String solutionRepositorySlug = projectKey.toLowerCase() + "-solution";
        SolutionProgrammingExerciseParticipation solutionParticipation = exercise.getSolutionParticipation();
        solutionParticipation.setRepositoryUrl(localVCBaseUrl + "/git/" + projectKey + "/" + solutionRepositorySlug + ".git");
        solutionProgrammingExerciseParticipationRepository.save(solutionParticipation);

        String assignmentRepositorySlug = projectKey.toLowerCase() + "-" + TEST_PREFIX + "student1";

        // Add a participation for student1.
        ProgrammingExerciseStudentParticipation studentParticipation = database.addStudentParticipationForProgrammingExercise(exercise, TEST_PREFIX + "student1");
        studentParticipation.setRepositoryUrl(String.format(localVCBaseUrl + "/git/%s/%s.git", projectKey, assignmentRepositorySlug));
        studentParticipation.setBranch(defaultBranch);
        programmingExerciseStudentParticipationRepository.save(studentParticipation);

        // Prepare the repositories.
        Path remoteTemplateRepositoryFolder = localVCLocalCITestService.createRepositoryFolderInTempDirectory(projectKey, templateRepositorySlug);
        LocalRepository templateRepository = new LocalRepository(defaultBranch);
        templateRepository.configureRepos("localTemplate", remoteTemplateRepositoryFolder);

        Path remoteTestsRepositoryFolder = localVCLocalCITestService.createRepositoryFolderInTempDirectory(projectKey, projectKey.toLowerCase() + "-tests");
        LocalRepository testsRepository = new LocalRepository(defaultBranch);
        testsRepository.configureRepos("localTests", remoteTestsRepositoryFolder);

        Path remoteSolutionRepositoryFolder = localVCLocalCITestService.createRepositoryFolderInTempDirectory(projectKey, solutionRepositorySlug);
        LocalRepository solutionRepository = new LocalRepository(defaultBranch);
        solutionRepository.configureRepos("localSolution", remoteSolutionRepositoryFolder);

        Path remoteAssignmentRepositoryFolder = localVCLocalCITestService.createRepositoryFolderInTempDirectory(projectKey, assignmentRepositorySlug);
        LocalRepository assignmentRepository = new LocalRepository(defaultBranch);
        assignmentRepository.configureRepos("localAssignment", remoteAssignmentRepositoryFolder);

        // Check that the repository folders were created in the file system for all repositories.
        LocalVCRepositoryUrl templateRepositoryUrl = new LocalVCRepositoryUrl(exercise.getTemplateRepositoryUrl(), localVCBaseUrl);
        assertThat(Files.exists(templateRepositoryUrl.getLocalRepositoryPath(localVCBasePath))).isTrue();
        LocalVCRepositoryUrl solutionRepositoryUrl = new LocalVCRepositoryUrl(exercise.getSolutionRepositoryUrl(), localVCBaseUrl);
        assertThat(Files.exists(solutionRepositoryUrl.getLocalRepositoryPath(localVCBasePath))).isTrue();
        LocalVCRepositoryUrl testsRepositoryUrl = new LocalVCRepositoryUrl(exercise.getTestRepositoryUrl(), localVCBaseUrl);
        assertThat(Files.exists(testsRepositoryUrl.getLocalRepositoryPath(localVCBasePath))).isTrue();

        // Delete the exercise
        var params = new LinkedMultiValueMap<String, String>();
        params.add("deleteStudentReposBuildPlans", "true");
        params.add("deleteBaseReposBuildPlans", "true");
        request.delete(ROOT + PROGRAMMING_EXERCISES + "/" + exercise.getId(), HttpStatus.OK, params);

        // Assert that the repository folders do not exist anymore.
        assertThat(Files.exists(templateRepositoryUrl.getLocalRepositoryPath(localVCBasePath))).isFalse();
        assertThat(Files.exists(solutionRepositoryUrl.getLocalRepositoryPath(localVCBasePath))).isFalse();
        assertThat(Files.exists(testsRepositoryUrl.getLocalRepositoryPath(localVCBasePath))).isFalse();
    }
}
