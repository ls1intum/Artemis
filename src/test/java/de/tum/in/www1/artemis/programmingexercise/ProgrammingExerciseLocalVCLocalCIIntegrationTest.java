package de.tum.in.www1.artemis.programmingexercise;

import static de.tum.in.www1.artemis.web.rest.ProgrammingExerciseResourceEndpoints.ROOT;
import static de.tum.in.www1.artemis.web.rest.ProgrammingExerciseResourceEndpoints.SETUP;

import java.time.ZonedDateTime;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationLocalCILocalVCTest;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.util.ModelFactory;

public class ProgrammingExerciseLocalVCLocalCIIntegrationTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "localvclocalciprogex";

    @Autowired
    private ProgrammingExerciseTestService programmingExerciseTestService;

    @Value("${artemis.continuous-integration.artemis-authentication-token-value}")
    private String ARTEMIS_AUTHENTICATION_TOKEN_VALUE;

    @BeforeEach
    void setup() throws Exception {
        programmingExerciseTestService.setupTestUsers(TEST_PREFIX, 0, 0, 0, 0);
        programmingExerciseTestService.setup(this, versionControlService, continuousIntegrationService);
    }

    @AfterEach
    void tearDown() throws Exception {
        programmingExerciseTestService.tearDown();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateProgrammingExercise() throws Exception {

        ProgrammingExercise newExercise = ModelFactory.generateProgrammingExercise(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(1), new Course());

        // Mock dockerClient.copyArchiveFromContainerCmd() such that it returns the XMLs containing the test results.
        // Mock the results for the template repository build and for the solution repository build that will both be triggered as a result of creating the exercise.
        Map<String, String> solutionBuildTestResults = localVCLocalCITestService.createMapFromTestResultsFolder(ALL_SUCCEED_TEST_RESULTS_PATH);
        Map<String, String> templateBuildTestResults = localVCLocalCITestService.createMapFromTestResultsFolder(ALL_FAIL_TEST_RESULTS_PATH);
        localVCLocalCITestService.mockInputStreamReturnedFromContainer(dockerClient, "/repositories/test-repository/build/test-results/test", templateBuildTestResults,
                solutionBuildTestResults);

        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.add("Authorization", ARTEMIS_AUTHENTICATION_TOKEN_VALUE);
        ProgrammingExercise createdExercise = request.postWithResponseBody(ROOT + SETUP, newExercise, ProgrammingExercise.class, HttpStatus.CREATED, httpHeaders);

        // Check that the repository folders were created in the file system for the template, solution, and tests repository.

        // Also check that the template and solution repositories were built successfully.
        localVCLocalCITestService.testLastestSubmission(createdExercise.getTemplateParticipation().getId(), null, 0, false);
        localVCLocalCITestService.testLastestSubmission(createdExercise.getSolutionParticipation().getId(), null, 13, false);
    }

    @Test
    void testCreateProgrammingExercise_projectExists() throws Exception {
        // Repository folder already exists in the file system.
        programmingExerciseTestService.createProgrammingExercise_sequential_validExercise_created(ProgrammingLanguage.JAVA);
    }

    @Test
    void testCreateProgrammingExercise_creatingProjectFolderFails() {
        // Creating the folder to house the repositories for the exercise fails.
    }

    @Test
    void testCreateProgrammingExercise_creatingRepositoryFails() {
        // Should throw a LocalVCException.
    }
}
