package de.tum.cit.aet.artemis.programming.icl;

import static de.tum.cit.aet.artemis.core.config.Constants.LOCALCI_RESULTS_DIRECTORY;
import static de.tum.cit.aet.artemis.core.config.Constants.LOCALCI_WORKING_DIRECTORY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.cit.aet.artemis.atlas.competency.util.CompetencyUtilService;
import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.core.connector.AeolusRequestMockProvider;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.programming.domain.AeolusTarget;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;
import de.tum.cit.aet.artemis.programming.domain.SolutionProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.TemplateProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.dto.CheckoutDirectoriesDTO;
import de.tum.cit.aet.artemis.programming.service.localvc.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.util.LocalRepository;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseFactory;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalCILocalVCTest;

// TestInstance.Lifecycle.PER_CLASS allows all test methods in this class to share the same instance of the test class.
// This reduces the overhead of repeatedly creating and tearing down a new Spring application context for each test method.
// This is especially useful when the test setup is expensive or when we want to share resources, such as database connections or mock objects, across multiple tests.
// In this case, we want to share the same GitService and UsernamePasswordCredentialsProvider.
@TestInstance(TestInstance.Lifecycle.PER_CLASS)

// ExecutionMode.SAME_THREAD ensures that all tests within this class are executed sequentially in the same thread, rather than in parallel or in a different thread.
// This is important in the context of LocalCI because it avoids potential race conditions or inconsistencies that could arise if multiple test methods are executed
// concurrently. For example, it prevents overloading the LocalCI's result processing system with too many build job results at the same time, which could lead to flaky tests
// or timeouts. By keeping everything in the same thread, we maintain more predictable and stable test behavior, while not increasing the test execution time significantly.
@Execution(ExecutionMode.SAME_THREAD)
class ProgrammingExerciseLocalVCLocalCIIntegrationTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "progexlocalvclocalci";

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private AeolusRequestMockProvider aeolusRequestMockProvider;

    @Autowired
    private CompetencyUtilService competencyUtilService;

    private Course course;

    private ProgrammingExercise programmingExercise;

    private LocalRepository templateRepository;

    private LocalRepository solutionRepository;

    private LocalRepository testsRepository;

    private LocalRepository assignmentRepository;

    private Competency competency;

    @Value("${artemis.user-management.internal-admin.username}")
    private String localVCUsername;

    @Value("${artemis.user-management.internal-admin.password}")
    private String localVCPassword;

    @BeforeAll
    void setupAll() {
        CredentialsProvider.setDefault(new UsernamePasswordCredentialsProvider(localVCUsername, localVCPassword));
    }

    @AfterAll
    void cleanupAll() {
        this.gitService.init();
    }

    @BeforeEach
    void setup() throws Exception {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 1, 1);

        course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        programmingExercise = exerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        String projectKey = programmingExercise.getProjectKey();
        programmingExercise.setProjectType(ProjectType.PLAIN_GRADLE);
        programmingExercise.setTestRepositoryUri(localVCBaseUrl + "/git/" + projectKey + "/" + projectKey.toLowerCase() + "-tests.git");
        programmingExerciseRepository.save(programmingExercise);
        programmingExercise = programmingExerciseRepository.findWithAllParticipationsAndBuildConfigById(programmingExercise.getId()).orElseThrow();

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

        competency = competencyUtilService.createCompetency(course);
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
        newExercise.setCompetencies(Set.of(competency));

        // Mock dockerClient.copyArchiveFromContainerCmd() such that it returns a dummy commitHash for both the assignment and the test repository.
        // Note: The stub needs to receive the same object twice because there are two requests to the same method (one for the template participation and one for the solution
        // participation).
        // Usually, specifying one doReturn() is enough to make the stub return the same object on every subsequent call.
        // However, in this case we have it return an InputStream, which will be consumed after returning it the first time, so we need to create two separate ones.
        localVCLocalCITestService.mockInputStreamReturnedFromContainer(dockerClient, LOCALCI_WORKING_DIRECTORY + "/testing-dir/assignment/.git/refs/heads/[^/]+",
                Map.of("assignmentCommitHash", DUMMY_COMMIT_HASH), Map.of("assignmentCommitHash", DUMMY_COMMIT_HASH));
        localVCLocalCITestService.mockInputStreamReturnedFromContainer(dockerClient, LOCALCI_WORKING_DIRECTORY + "/testing-dir/.git/refs/heads/[^/]+",
                Map.of("testsCommitHash", DUMMY_COMMIT_HASH), Map.of("testsCommitHash", DUMMY_COMMIT_HASH));

        localVCLocalCITestService.mockInspectImage(dockerClient);

        // Mock dockerClient.copyArchiveFromContainerCmd() such that it returns the XMLs containing the test results.
        // Mock the results for the template repository build and for the solution repository build that will both be triggered as a result of creating the exercise.
        Map<String, String> templateBuildTestResults = localVCLocalCITestService.createMapFromTestResultsFolder(ALL_FAIL_TEST_RESULTS_PATH);
        Map<String, String> solutionBuildTestResults = localVCLocalCITestService.createMapFromTestResultsFolder(ALL_SUCCEED_TEST_RESULTS_PATH);
        localVCLocalCITestService.mockInputStreamReturnedFromContainer(dockerClient, LOCALCI_WORKING_DIRECTORY + LOCALCI_RESULTS_DIRECTORY, templateBuildTestResults,
                solutionBuildTestResults);
        newExercise.setChannelName("testchannelname-pe");
        aeolusRequestMockProvider.enableMockingOfRequests();
        aeolusRequestMockProvider.mockFailedGenerateBuildPlan(AeolusTarget.CLI);
        ProgrammingExercise createdExercise = request.postWithResponseBody("/api/programming-exercises/setup", newExercise, ProgrammingExercise.class, HttpStatus.CREATED);

        // Check that the repository folders were created in the file system for the template, solution, and tests repository.
        localVCLocalCITestService.verifyRepositoryFoldersExist(createdExercise, localVCBasePath);

        // Also check that the template and solution repositories were built successfully.
        localVCLocalCITestService.testLatestSubmission(createdExercise.getTemplateParticipation().getId(), null, 0, false);
        localVCLocalCITestService.testLatestSubmission(createdExercise.getSolutionParticipation().getId(), null, 13, false);

        verify(competencyProgressService).updateProgressByLearningObjectAsync(eq(createdExercise));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateProgrammingExercise() throws Exception {
        programmingExercise.setReleaseDate(ZonedDateTime.now().plusHours(1));
        programmingExercise.setCompetencies(Set.of(competency));

        ProgrammingExercise updatedExercise = request.putWithResponseBody("/api/programming-exercises", programmingExercise, ProgrammingExercise.class, HttpStatus.OK);

        assertThat(updatedExercise.getReleaseDate()).isEqualTo(programmingExercise.getReleaseDate());
        verify(competencyProgressService, timeout(1000).times(1)).updateProgressForUpdatedLearningObjectAsync(eq(programmingExercise), eq(Optional.of(programmingExercise)));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateProgrammingExercise_templateRepositoryUriIsInvalid() throws Exception {
        programmingExercise.setTemplateRepositoryUri("http://localhost:9999/some/invalid/url.git");
        request.put("/api/programming-exercises", programmingExercise, HttpStatus.BAD_REQUEST);

        programmingExercise.setTemplateRepositoryUri(
                "http://localhost:49152/invalidUrlMapping/" + programmingExercise.getProjectKey() + "/" + programmingExercise.getProjectKey().toLowerCase() + "-exercise.git");
        request.put("/api/programming-exercises", programmingExercise, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteProgrammingExercise() throws Exception {
        programmingExercise.setCompetencies(Set.of(competency));
        programmingExerciseRepository.save(programmingExercise);

        // Delete the exercise
        var params = new LinkedMultiValueMap<String, String>();
        params.add("deleteStudentReposBuildPlans", "true");
        params.add("deleteBaseReposBuildPlans", "true");
        request.delete("/api/programming-exercises/" + programmingExercise.getId(), HttpStatus.OK, params);

        // Assert that the repository folders do not exist anymore.
        LocalVCRepositoryUri templateRepositoryUri = new LocalVCRepositoryUri(programmingExercise.getTemplateRepositoryUri());
        assertThat(templateRepositoryUri.getLocalRepositoryPath(localVCBasePath)).doesNotExist();
        LocalVCRepositoryUri solutionRepositoryUri = new LocalVCRepositoryUri(programmingExercise.getSolutionRepositoryUri());
        assertThat(solutionRepositoryUri.getLocalRepositoryPath(localVCBasePath)).doesNotExist();
        LocalVCRepositoryUri testsRepositoryUri = new LocalVCRepositoryUri(programmingExercise.getTestRepositoryUri());
        assertThat(testsRepositoryUri.getLocalRepositoryPath(localVCBasePath)).doesNotExist();
        verify(competencyProgressService).updateProgressByCompetencyAsync(eq(competency));
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

        localVCLocalCITestService.mockInspectImage(dockerClient);

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
        exerciseToBeImported.setCompetencies(Set.of(competency));
        var importedExercise = request.postWithResponseBody("/api/programming-exercises/import/" + programmingExercise.getId(), exerciseToBeImported, ProgrammingExercise.class,
                params, HttpStatus.OK);

        // Assert that the repositories were correctly created for the imported exercise.
        ProgrammingExercise importedExerciseWithParticipations = programmingExerciseRepository.findWithAllParticipationsAndBuildConfigById(importedExercise.getId()).orElseThrow();
        localVCLocalCITestService.verifyRepositoryFoldersExist(importedExerciseWithParticipations, localVCBasePath);

        // Also check that the template and solution repositories were built successfully.
        TemplateProgrammingExerciseParticipation templateParticipation = templateProgrammingExerciseParticipationRepository.findByProgrammingExerciseId(importedExercise.getId())
                .orElseThrow();
        SolutionProgrammingExerciseParticipation solutionParticipation = solutionProgrammingExerciseParticipationRepository.findByProgrammingExerciseId(importedExercise.getId())
                .orElseThrow();
        localVCLocalCITestService.testLatestSubmission(templateParticipation.getId(), null, 0, false);
        localVCLocalCITestService.testLatestSubmission(solutionParticipation.getId(), null, 13, false);
        verify(competencyProgressService).updateProgressByLearningObjectAsync(eq(importedExercise));
    }

    @Nested
    class TestGetCheckoutDirectories {

        @Test
        @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
        void testWithValidProgrammingLanguage() throws Exception {
            CheckoutDirectoriesDTO checkoutDirectoryDTO = request.get("/api/programming-exercises/repository-checkout-directories?programmingLanguage=JAVA", HttpStatus.OK,
                    CheckoutDirectoriesDTO.class);

            assertThat(checkoutDirectoryDTO.submissionBuildPlanCheckoutDirectories().exerciseCheckoutDirectory()).isEqualTo("/assignment");
            assertThat(checkoutDirectoryDTO.submissionBuildPlanCheckoutDirectories().solutionCheckoutDirectory()).isNull();
            assertThat(checkoutDirectoryDTO.submissionBuildPlanCheckoutDirectories().testCheckoutDirectory()).isEqualTo("/");

            // Verify solution build plan checkout directories
            assertThat(checkoutDirectoryDTO.solutionBuildPlanCheckoutDirectories().exerciseCheckoutDirectory()).isEqualTo(null);
            assertThat(checkoutDirectoryDTO.solutionBuildPlanCheckoutDirectories().solutionCheckoutDirectory()).isEqualTo("/assignment");
            assertThat(checkoutDirectoryDTO.solutionBuildPlanCheckoutDirectories().testCheckoutDirectory()).isEqualTo("/");
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
        void testWithNotSupportedProgrammingLanguage() throws Exception {
            request.get("/api/programming-exercises/repository-checkout-directories?programmingLanguage=languageThatDoesNotExist", HttpStatus.BAD_REQUEST,
                    CheckoutDirectoriesDTO.class);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
        void testAccessForbidden() throws Exception {
            request.get("/api/programming-exercises/repository-checkout-directories?programmingLanguage=JAVA", HttpStatus.FORBIDDEN, CheckoutDirectoriesDTO.class);
        }
    }
}
