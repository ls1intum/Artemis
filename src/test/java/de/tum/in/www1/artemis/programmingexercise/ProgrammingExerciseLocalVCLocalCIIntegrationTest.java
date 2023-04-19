package de.tum.in.www1.artemis.programmingexercise;

import static de.tum.in.www1.artemis.web.rest.ProgrammingExerciseResourceEndpoints.IMPORT;
import static de.tum.in.www1.artemis.web.rest.ProgrammingExerciseResourceEndpoints.PROGRAMMING_EXERCISES;
import static de.tum.in.www1.artemis.web.rest.ProgrammingExerciseResourceEndpoints.ROOT;
import static de.tum.in.www1.artemis.web.rest.ProgrammingExerciseResourceEndpoints.SETUP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import java.io.IOException;
import java.nio.file.Files;
import java.time.ZonedDateTime;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
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

class ProgrammingExerciseLocalVCLocalCIIntegrationTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "progexlocalvclocalci";

    private Course course;

    private ProgrammingExercise programmingExercise;

    LocalRepository templateRepository;

    LocalRepository solutionRepository;

    LocalRepository testsRepository;

    LocalRepository assignmentRepository;

    @BeforeEach
    void setup() throws Exception {
        database.addUsers(TEST_PREFIX, 1, 1, 0, 1);

        course = database.addCourseWithOneProgrammingExercise();
        programmingExercise = database.getFirstExerciseWithType(course, ProgrammingExercise.class);
        String projectKey = programmingExercise.getProjectKey();
        programmingExercise.setTestRepositoryUrl(localVCBaseUrl + "/git/" + projectKey + "/" + projectKey.toLowerCase() + "-tests.git");
        programmingExerciseRepository.save(programmingExercise);
        programmingExercise = programmingExerciseRepository.findWithAllParticipationsById(programmingExercise.getId()).orElseThrow();

        // Set the correct repository URLs for the template and the solution participation.
        String templateRepositorySlug = projectKey.toLowerCase() + "-exercise";
        TemplateProgrammingExerciseParticipation templateParticipation = programmingExercise.getTemplateParticipation();
        templateParticipation.setRepositoryUrl(localVCBaseUrl + "/git/" + projectKey + "/" + templateRepositorySlug + ".git");
        templateProgrammingExerciseParticipationRepository.save(templateParticipation);
        String solutionRepositorySlug = projectKey.toLowerCase() + "-solution";
        SolutionProgrammingExerciseParticipation solutionParticipation = programmingExercise.getSolutionParticipation();
        solutionParticipation.setRepositoryUrl(localVCBaseUrl + "/git/" + projectKey + "/" + solutionRepositorySlug + ".git");
        solutionProgrammingExerciseParticipationRepository.save(solutionParticipation);

        String assignmentRepositorySlug = projectKey.toLowerCase() + "-" + TEST_PREFIX + "student1";

        // Add a participation for student1.
        ProgrammingExerciseStudentParticipation studentParticipation = database.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student1");
        studentParticipation.setRepositoryUrl(String.format(localVCBaseUrl + "/git/%s/%s.git", projectKey, assignmentRepositorySlug));
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
        mockDockerClientMethods();

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
        localVCLocalCITestService.verifyRepositoryFoldersExist(createdExercise, localVCBasePath);

        // Also check that the template and solution repositories were built successfully.
        localVCLocalCITestService.testLastestSubmission(createdExercise.getTemplateParticipation().getId(), null, 0, false);
        localVCLocalCITestService.testLastestSubmission(createdExercise.getSolutionParticipation().getId(), null, 13, false);
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
    void testDeleteProgrammingExercise() throws Exception {
        // Delete the exercise
        var params = new LinkedMultiValueMap<String, String>();
        params.add("deleteStudentReposBuildPlans", "true");
        params.add("deleteBaseReposBuildPlans", "true");
        request.delete(ROOT + PROGRAMMING_EXERCISES + "/" + programmingExercise.getId(), HttpStatus.OK, params);

        // Assert that the repository folders do not exist anymore.
        LocalVCRepositoryUrl templateRepositoryUrl = new LocalVCRepositoryUrl(programmingExercise.getTemplateRepositoryUrl(), localVCBaseUrl);
        assertThat(Files.exists(templateRepositoryUrl.getLocalRepositoryPath(localVCBasePath))).isFalse();
        LocalVCRepositoryUrl solutionRepositoryUrl = new LocalVCRepositoryUrl(programmingExercise.getSolutionRepositoryUrl(), localVCBaseUrl);
        assertThat(Files.exists(solutionRepositoryUrl.getLocalRepositoryPath(localVCBasePath))).isFalse();
        LocalVCRepositoryUrl testsRepositoryUrl = new LocalVCRepositoryUrl(programmingExercise.getTestRepositoryUrl(), localVCBaseUrl);
        assertThat(Files.exists(testsRepositoryUrl.getLocalRepositoryPath(localVCBasePath))).isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testImportProgrammingExercise() throws Exception {
        ProgrammingExercise exerciseToBeImported = ModelFactory.generateToBeImportedProgrammingExercise("ImportTitle", "imported", programmingExercise, database.addEmptyCourse());

        // Import the exercise and load all referenced entities
        var params = new LinkedMultiValueMap<String, String>();
        params.add("recreateBuildPlans", "true");
        var importedExercise = request.postWithResponseBody(ROOT + IMPORT.replace("{sourceExerciseId}", programmingExercise.getId().toString()), exerciseToBeImported,
                ProgrammingExercise.class, params, HttpStatus.OK);

        // Assert that the repositories were correctly created for the imported exercise.
        ProgrammingExercise importedExerciseWithParticipations = programmingExerciseRepository.findWithAllParticipationsById(importedExercise.getId()).orElseThrow();
        localVCLocalCITestService.verifyRepositoryFoldersExist(importedExerciseWithParticipations, localVCBasePath);
    }
}
