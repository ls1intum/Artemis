package de.tum.in.www1.artemis.exercise.programmingexercise;

import static de.tum.in.www1.artemis.config.Constants.LOCALCI_RESULTS_DIRECTORY;
import static de.tum.in.www1.artemis.config.Constants.LOCALCI_WORKING_DIRECTORY;
import static de.tum.in.www1.artemis.web.rest.programming.ProgrammingExerciseResourceEndpoints.IMPORT;
import static de.tum.in.www1.artemis.web.rest.programming.ProgrammingExerciseResourceEndpoints.PROGRAMMING_EXERCISES;
import static de.tum.in.www1.artemis.web.rest.programming.ProgrammingExerciseResourceEndpoints.ROOT;
import static de.tum.in.www1.artemis.web.rest.programming.ProgrammingExerciseResourceEndpoints.SETUP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.in.www1.artemis.AbstractSpringIntegrationLocalCILocalVCTest;
import de.tum.in.www1.artemis.connector.AeolusRequestMockProvider;
import de.tum.in.www1.artemis.course.CourseUtilService;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.enumeration.AeolusTarget;
import de.tum.in.www1.artemis.domain.enumeration.ProjectType;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.participation.SolutionProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.TemplateProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.exercise.ExerciseUtilService;
import de.tum.in.www1.artemis.participation.ParticipationUtilService;
import de.tum.in.www1.artemis.service.connectors.localvc.LocalVCRepositoryUri;
import de.tum.in.www1.artemis.util.LocalRepository;

class ProgrammingExerciseLocalVCLocalCIIntegrationTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "progexlocalvclocalci";

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private ExerciseUtilService exerciseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private AeolusRequestMockProvider aeolusRequestMockProvider;

    private Course course;

    private ProgrammingExercise programmingExercise;

    LocalRepository templateRepository;

    LocalRepository solutionRepository;

    LocalRepository testsRepository;

    LocalRepository assignmentRepository;

    @BeforeEach
    void setup() throws Exception {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 0, 1);

        course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        programmingExercise = exerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        String projectKey = programmingExercise.getProjectKey();
        programmingExercise.setProjectType(ProjectType.PLAIN_GRADLE);
        programmingExercise.setTestRepositoryUri(localVCBaseUrl + "/git/" + projectKey + "/" + projectKey.toLowerCase() + "-tests.git");
        programmingExerciseRepository.save(programmingExercise);
        programmingExercise = programmingExerciseRepository.findWithAllParticipationsById(programmingExercise.getId()).orElseThrow();

        // Set the correct repository URIs for the template and the solution participation.
        String templateRepositorySlug = projectKey.toLowerCase() + "-exercise";
        TemplateProgrammingExerciseParticipation templateParticipation = programmingExercise.getTemplateParticipation();
        templateParticipation.setRepositoryUri(localVCBaseUrl + "/git/" + projectKey + "/" + templateRepositorySlug + ".git");
        templateProgrammingExerciseParticipationRepository.save(templateParticipation);
        String solutionRepositorySlug = projectKey.toLowerCase() + "-solution";
        SolutionProgrammingExerciseParticipation solutionParticipation = programmingExercise.getSolutionParticipation();
        solutionParticipation.setRepositoryUri(localVCBaseUrl + "/git/" + projectKey + "/" + solutionRepositorySlug + ".git");
        solutionProgrammingExerciseParticipationRepository.save(solutionParticipation);

        String assignmentRepositorySlug = projectKey.toLowerCase() + "-" + TEST_PREFIX + "student1";

        // Add a participation for student1.
        ProgrammingExerciseStudentParticipation studentParticipation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise,
                TEST_PREFIX + "student1");
        studentParticipation.setRepositoryUri(String.format(localVCBaseUrl + "/git/%s/%s.git", projectKey, assignmentRepositorySlug));
        studentParticipation.setBranch(defaultBranch);
        programmingExerciseStudentParticipationRepository.save(studentParticipation);

        // Prepare the repositories.
        templateRepository = localVCLocalCITestService.createAndConfigureLocalRepository(projectKey, templateRepositorySlug);
        testsRepository = localVCLocalCITestService.createAndConfigureLocalRepository(projectKey, projectKey.toLowerCase() + "-tests");
        solutionRepository = localVCLocalCITestService.createAndConfigureLocalRepository(projectKey, solutionRepositorySlug);
        assignmentRepository = localVCLocalCITestService.createAndConfigureLocalRepository(projectKey, assignmentRepositorySlug);

        // Check that the repository folders were created in the file system for all base repositories.
        localVCLocalCITestService.verifyRepositoryFoldersExist(programmingExercise, localVCBasePath);
    }

    @AfterEach
    void tearDown() throws IOException {
        templateRepository.resetLocalRepo();
        solutionRepository.resetLocalRepo();
        testsRepository.resetLocalRepo();
        assignmentRepository.resetLocalRepo();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateProgrammingExercise() throws Exception {
        // The mock commit hashes don't allow the getPushDate() method in the LocalVCService to retrieve the push date using the commit hash. Thus, this method must be mocked.
        doReturn(ZonedDateTime.now().minusSeconds(2)).when(versionControlService).getPushDate(any(), any(), any());

        ProgrammingExercise newExercise = ProgrammingExerciseFactory.generateProgrammingExercise(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(7), course);
        newExercise.setProjectType(ProjectType.PLAIN_GRADLE);

        // Mock dockerClient.copyArchiveFromContainerCmd() such that it returns a dummy commitHash for both the assignment and the test repository.
        // Note: The stub needs to receive the same object twice because there are two requests to the same method (one for the template participation and one for the solution
        // participation).
        // Usually, specifying one doReturn() is enough to make the stub return the same object on every subsequent call.
        // However, in this case we have it return an InputStream, which will be consumed after returning it the first time, so we need to create two separate ones.
        localVCLocalCITestService.mockInputStreamReturnedFromContainer(dockerClient, LOCALCI_WORKING_DIRECTORY + "/testing-dir/assignment/.git/refs/heads/[^/]+",
                Map.of("assignmentCommitHash", DUMMY_COMMIT_HASH), Map.of("assignmentCommitHash", DUMMY_COMMIT_HASH));
        localVCLocalCITestService.mockInputStreamReturnedFromContainer(dockerClient, LOCALCI_WORKING_DIRECTORY + "/testing-dir/.git/refs/heads/[^/]+",
                Map.of("testsCommitHash", DUMMY_COMMIT_HASH), Map.of("testsCommitHash", DUMMY_COMMIT_HASH));

        // Mock dockerClient.copyArchiveFromContainerCmd() such that it returns the XMLs containing the test results.
        // Mock the results for the template repository build and for the solution repository build that will both be triggered as a result of creating the exercise.
        Map<String, String> templateBuildTestResults = localVCLocalCITestService.createMapFromTestResultsFolder(ALL_FAIL_TEST_RESULTS_PATH);
        Map<String, String> solutionBuildTestResults = localVCLocalCITestService.createMapFromTestResultsFolder(ALL_SUCCEED_TEST_RESULTS_PATH);
        localVCLocalCITestService.mockInputStreamReturnedFromContainer(dockerClient, LOCALCI_WORKING_DIRECTORY + LOCALCI_RESULTS_DIRECTORY, templateBuildTestResults,
                solutionBuildTestResults);
        newExercise.setChannelName("testchannelname-pe");
        aeolusRequestMockProvider.enableMockingOfRequests();
        aeolusRequestMockProvider.mockFailedGenerateBuildPlan(AeolusTarget.CLI);
        ProgrammingExercise createdExercise = request.postWithResponseBody(ROOT + SETUP, newExercise, ProgrammingExercise.class, HttpStatus.CREATED);

        // Check that the repository folders were created in the file system for the template, solution, and tests repository.
        localVCLocalCITestService.verifyRepositoryFoldersExist(createdExercise, localVCBasePath);

        // Also check that the template and solution repositories were built successfully.
        localVCLocalCITestService.testLatestSubmission(createdExercise.getTemplateParticipation().getId(), null, 0, false);
        localVCLocalCITestService.testLatestSubmission(createdExercise.getSolutionParticipation().getId(), null, 13, false);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateProgrammingExercise() throws Exception {
        programmingExercise.setReleaseDate(ZonedDateTime.now().plusHours(1));

        ProgrammingExercise updatedExercise = request.putWithResponseBody(ROOT + PROGRAMMING_EXERCISES, programmingExercise, ProgrammingExercise.class, HttpStatus.OK);

        assertThat(updatedExercise.getReleaseDate()).isEqualTo(programmingExercise.getReleaseDate());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateProgrammingExercise_templateRepositoryUriIsInvalid() throws Exception {
        programmingExercise.setTemplateRepositoryUri("http://localhost:9999/some/invalid/url.git");
        request.put(ROOT + PROGRAMMING_EXERCISES, programmingExercise, HttpStatus.BAD_REQUEST);

        programmingExercise.setTemplateRepositoryUri(
                "http://localhost:49152/invalidUrlMapping/" + programmingExercise.getProjectKey() + "/" + programmingExercise.getProjectKey().toLowerCase() + "-exercise.git");
        request.put(ROOT + PROGRAMMING_EXERCISES, programmingExercise, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteProgrammingExercise() throws Exception {
        // Delete the exercise
        var params = new LinkedMultiValueMap<String, String>();
        params.add("deleteStudentReposBuildPlans", "true");
        params.add("deleteBaseReposBuildPlans", "true");
        request.delete(ROOT + PROGRAMMING_EXERCISES + "/" + programmingExercise.getId(), HttpStatus.OK, params);

        // Assert that the repository folders do not exist anymore.
        LocalVCRepositoryUri templateRepositoryUri = new LocalVCRepositoryUri(programmingExercise.getTemplateRepositoryUri(), localVCBaseUrl);
        assertThat(templateRepositoryUri.getLocalRepositoryPath(localVCBasePath)).doesNotExist();
        LocalVCRepositoryUri solutionRepositoryUri = new LocalVCRepositoryUri(programmingExercise.getSolutionRepositoryUri(), localVCBaseUrl);
        assertThat(solutionRepositoryUri.getLocalRepositoryPath(localVCBasePath)).doesNotExist();
        LocalVCRepositoryUri testsRepositoryUri = new LocalVCRepositoryUri(programmingExercise.getTestRepositoryUri(), localVCBaseUrl);
        assertThat(testsRepositoryUri.getLocalRepositoryPath(localVCBasePath)).doesNotExist();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testImportProgrammingExercise() throws Exception {
        // The mock commit hashes don't allow the getPushDate() method in the LocalVCService to retrieve the push date using the commit hash. Thus, this method must be mocked.
        doReturn(ZonedDateTime.now().minusSeconds(2)).when(versionControlService).getPushDate(any(), any(), any());

        // Mock dockerClient.copyArchiveFromContainerCmd() such that it returns a dummy commitHash for both the assignment and the test repository.
        // Note: The stub needs to receive the same object twice because there are two requests to the same method (one for the template participation and one for the solution
        // participation).
        // Usually, specifying one doReturn() is enough to make the stub return the same object on every subsequent call.
        // However, in this case we have it return an InputStream, which will be consumed after returning it the first time, so we need to create two separate ones.
        localVCLocalCITestService.mockInputStreamReturnedFromContainer(dockerClient, LOCALCI_WORKING_DIRECTORY + "/testing-dir/assignment/.git/refs/heads/[^/]+",
                Map.of("assignmentComitHash", DUMMY_COMMIT_HASH), Map.of("assignmentComitHash", DUMMY_COMMIT_HASH));
        localVCLocalCITestService.mockInputStreamReturnedFromContainer(dockerClient, LOCALCI_WORKING_DIRECTORY + "/testing-dir/.git/refs/heads/[^/]+",
                Map.of("testsCommitHash", DUMMY_COMMIT_HASH), Map.of("testsCommitHash", DUMMY_COMMIT_HASH));

        // Mock dockerClient.copyArchiveFromContainerCmd() such that it returns the XMLs containing the test results.
        // Mock the results for the template repository build and for the solution repository build that will both be triggered as a result of creating the exercise.
        Map<String, String> templateBuildTestResults = localVCLocalCITestService.createMapFromTestResultsFolder(ALL_FAIL_TEST_RESULTS_PATH);
        Map<String, String> solutionBuildTestResults = localVCLocalCITestService.createMapFromTestResultsFolder(ALL_SUCCEED_TEST_RESULTS_PATH);
        localVCLocalCITestService.mockInputStreamReturnedFromContainer(dockerClient, LOCALCI_WORKING_DIRECTORY + LOCALCI_RESULTS_DIRECTORY, templateBuildTestResults,
                solutionBuildTestResults);

        ProgrammingExercise exerciseToBeImported = ProgrammingExerciseFactory.generateToBeImportedProgrammingExercise("ImportTitle", "imported", programmingExercise,
                courseUtilService.addEmptyCourse());

        // Import the exercise and load all referenced entities
        var params = new LinkedMultiValueMap<String, String>();
        params.add("recreateBuildPlans", "true");
        exerciseToBeImported.setChannelName("testchannel-pe-imported");
        var importedExercise = request.postWithResponseBody(ROOT + IMPORT.replace("{sourceExerciseId}", programmingExercise.getId().toString()), exerciseToBeImported,
                ProgrammingExercise.class, params, HttpStatus.OK);

        // Assert that the repositories were correctly created for the imported exercise.
        ProgrammingExercise importedExerciseWithParticipations = programmingExerciseRepository.findWithAllParticipationsById(importedExercise.getId()).orElseThrow();
        localVCLocalCITestService.verifyRepositoryFoldersExist(importedExerciseWithParticipations, localVCBasePath);

        // Also check that the template and solution repositories were built successfully.
        TemplateProgrammingExerciseParticipation templateParticipation = templateProgrammingExerciseParticipationRepository.findByProgrammingExerciseId(importedExercise.getId())
                .orElseThrow();
        SolutionProgrammingExerciseParticipation solutionParticipation = solutionProgrammingExerciseParticipationRepository.findByProgrammingExerciseId(importedExercise.getId())
                .orElseThrow();
        localVCLocalCITestService.testLatestSubmission(templateParticipation.getId(), null, 0, false);
        localVCLocalCITestService.testLatestSubmission(solutionParticipation.getId(), null, 13, false);
    }
}
