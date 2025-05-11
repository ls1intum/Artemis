package de.tum.cit.aet.artemis.programming.icl;

import static de.tum.cit.aet.artemis.core.config.Constants.LOCAL_CI_RESULTS_DIRECTORY;
import static de.tum.cit.aet.artemis.core.config.Constants.LOCAL_CI_WORKING_DIRECTORY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyExerciseLink;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.exam.util.InvalidExamExerciseDatesArgumentProvider;
import de.tum.cit.aet.artemis.exam.util.InvalidExamExerciseDatesArgumentProvider.InvalidExamExerciseDateConfiguration;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationLocalCILocalVCTest;

import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationLocalCILocalVCTestBase;
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
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseTestService;

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
class ProgrammingExerciseLocalVCLocalCIIntegrationTest extends AbstractProgrammingIntegrationLocalCILocalVCTestBase {

    private static final String TEST_PREFIX = "progexlocalvclocalci";

    private Course course;

    private ProgrammingExercise programmingExercise;

    private LocalRepository templateRepository;

    private LocalRepository solutionRepository;

    private LocalRepository testsRepository;

    private LocalRepository assignmentRepository;

    private Competency competency;

    @org.springframework.beans.factory.annotation.Autowired
    private ProgrammingExerciseTestService programmingExerciseTestService;

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

        programmingExerciseTestService.setupTestUsers(TEST_PREFIX, 0, 0, 0, 0);
        programmingExerciseTestService.setup(this, versionControlService, localVCGitBranchService);
    }

    @Override
    protected String getTestPrefix() {
        return TEST_PREFIX;
    }

    @AfterEach
    void tearDown() throws Exception {
        templateRepository.resetLocalRepo();
        solutionRepository.resetLocalRepo();
        testsRepository.resetLocalRepo();
        assignmentRepository.resetLocalRepo();
        programmingExerciseTestService.tearDown();
    }

    @Disabled
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateProgrammingExercise() throws Exception {
        ProgrammingExercise newExercise = ProgrammingExerciseFactory.generateProgrammingExercise(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(7), course);
        newExercise.setProjectType(ProjectType.PLAIN_GRADLE);
        newExercise.setCompetencyLinks(Set.of(new CompetencyExerciseLink(competency, newExercise, 1)));
        newExercise.getCompetencyLinks().forEach(link -> link.getCompetency().setCourse(null));

        // Mock dockerClient.copyArchiveFromContainerCmd() such that it returns a dummy commitHash for both the assignment and the test repository.
        // Note: The stub needs to receive the same object twice because there are two requests to the same method (one for the template participation and one for the solution
        // participation).
        // Usually, specifying one doReturn() is enough to make the stub return the same object on every subsequent call.
        // However, in this case we have it return an InputStream, which will be consumed after returning it the first time, so we need to create two separate ones.
        dockerClientTestService.mockInputStreamReturnedFromContainer(dockerClient, LOCAL_CI_WORKING_DIRECTORY + "/testing-dir/assignment/.git/refs/heads/[^/]+",
                Map.of("assignmentCommitHash", DUMMY_COMMIT_HASH), Map.of("assignmentCommitHash", DUMMY_COMMIT_HASH));
        dockerClientTestService.mockInputStreamReturnedFromContainer(dockerClient, LOCAL_CI_WORKING_DIRECTORY + "/testing-dir/.git/refs/heads/[^/]+",
                Map.of("testsCommitHash", DUMMY_COMMIT_HASH), Map.of("testsCommitHash", DUMMY_COMMIT_HASH));

        dockerClientTestService.mockInspectImage(dockerClient);

        // Mock dockerClient.copyArchiveFromContainerCmd() such that it returns the XMLs containing the test results.
        // Mock the results for the template repository build and for the solution repository build that will both be triggered as a result of creating the exercise.
        Map<String, String> templateBuildTestResults = dockerClientTestService.createMapFromTestResultsFolder(ALL_FAIL_TEST_RESULTS_PATH);
        Map<String, String> solutionBuildTestResults = dockerClientTestService.createMapFromTestResultsFolder(ALL_SUCCEED_TEST_RESULTS_PATH);
        dockerClientTestService.mockInputStreamReturnedFromContainer(dockerClient, LOCAL_CI_WORKING_DIRECTORY + LOCAL_CI_RESULTS_DIRECTORY, templateBuildTestResults,
                solutionBuildTestResults);
        newExercise.setChannelName("testchannelname-pe");
        aeolusRequestMockProvider.enableMockingOfRequests();
        aeolusRequestMockProvider.mockFailedGenerateBuildPlan(AeolusTarget.CLI);
        ProgrammingExercise createdExercise = request.postWithResponseBody("/api/programming/programming-exercises/setup", newExercise, ProgrammingExercise.class,
                HttpStatus.CREATED);

        // Check that the repository folders were created in the file system for the template, solution, and tests repository.
        localVCLocalCITestService.verifyRepositoryFoldersExist(createdExercise, localVCBasePath);

        // Also check that the template and solution repositories were built successfully.
        localVCLocalCITestService.testLatestSubmission(createdExercise.getTemplateParticipation().getId(), null, 0, false);
        localVCLocalCITestService.testLatestSubmission(createdExercise.getSolutionParticipation().getId(), null, 13, false);

        verify(competencyProgressApi).updateProgressByLearningObjectAsync(eq(createdExercise));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateProgrammingExercise_Invalid_CheckoutPaths() throws Exception {

        ProgrammingExercise newExercise = ProgrammingExerciseFactory.generateProgrammingExercise(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(7), course);
        newExercise.setProjectType(ProjectType.PLAIN_GRADLE);
        newExercise.getBuildConfig().setAssignmentCheckoutPath("/invalid/assignment");

        request.postWithResponseBody("/api/programming/programming-exercises/setup", newExercise, ProgrammingExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateProgrammingExercise() throws Exception {
        programmingExercise.setReleaseDate(ZonedDateTime.now().plusHours(1));
        programmingExercise.setCompetencyLinks(Set.of(new CompetencyExerciseLink(competency, programmingExercise, 1)));
        programmingExercise.getCompetencyLinks().forEach(link -> link.getCompetency().setCourse(null));

        ProgrammingExercise updatedExercise = request.putWithResponseBody("/api/programming/programming-exercises", programmingExercise, ProgrammingExercise.class, HttpStatus.OK);

        assertThat(updatedExercise.getReleaseDate()).isEqualTo(programmingExercise.getReleaseDate());
        verify(competencyProgressApi, timeout(1000).times(1)).updateProgressForUpdatedLearningObjectAsync(eq(programmingExercise), eq(Optional.of(programmingExercise)));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateProgrammingExercise_templateRepositoryUriIsInvalid() throws Exception {
        programmingExercise.setTemplateRepositoryUri("http://localhost:9999/some/invalid/url.git");
        request.put("/api/programming/programming-exercises", programmingExercise, HttpStatus.BAD_REQUEST);

        programmingExercise.setTemplateRepositoryUri(
                "http://localhost:49152/invalidUrlMapping/" + programmingExercise.getProjectKey() + "/" + programmingExercise.getProjectKey().toLowerCase() + "-exercise.git");
        request.put("/api/programming/programming-exercises", programmingExercise, HttpStatus.BAD_REQUEST);
    }

    @Disabled
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteProgrammingExercise() throws Exception {
        programmingExercise.setCompetencyLinks(Set.of(new CompetencyExerciseLink(competency, programmingExercise, 1)));
        programmingExerciseRepository.save(programmingExercise);

        // Delete the exercise
        var params = new LinkedMultiValueMap<String, String>();
        params.add("deleteStudentReposBuildPlans", "true");
        params.add("deleteBaseReposBuildPlans", "true");
        request.delete("/api/programming/programming-exercises/" + programmingExercise.getId(), HttpStatus.OK, params);

        // Assert that the repository folders do not exist anymore.
        LocalVCRepositoryUri templateRepositoryUri = new LocalVCRepositoryUri(programmingExercise.getTemplateRepositoryUri());
        assertThat(templateRepositoryUri.getLocalRepositoryPath(localVCBasePath)).doesNotExist();
        LocalVCRepositoryUri solutionRepositoryUri = new LocalVCRepositoryUri(programmingExercise.getSolutionRepositoryUri());
        assertThat(solutionRepositoryUri.getLocalRepositoryPath(localVCBasePath)).doesNotExist();
        LocalVCRepositoryUri testsRepositoryUri = new LocalVCRepositoryUri(programmingExercise.getTestRepositoryUri());
        assertThat(testsRepositoryUri.getLocalRepositoryPath(localVCBasePath)).doesNotExist();
        verify(competencyProgressApi).updateProgressByCompetencyAsync(eq(competency));
    }

    @Disabled
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testImportProgrammingExercise() throws Exception {
        // Mock dockerClient.copyArchiveFromContainerCmd() such that it returns a dummy commitHash for both the assignment and the test repository.
        // Note: The stub needs to receive the same object twice because there are two requests to the same method (one for the template participation and one for the solution
        // participation).
        // Usually, specifying one doReturn() is enough to make the stub return the same object on every subsequent call.
        // However, in this case we have it return an InputStream, which will be consumed after returning it the first time, so we need to create two separate ones.
        dockerClientTestService.mockInputStreamReturnedFromContainer(dockerClient, LOCAL_CI_WORKING_DIRECTORY + "/testing-dir/assignment/.git/refs/heads/[^/]+",
                Map.of("assignmentComitHash", DUMMY_COMMIT_HASH), Map.of("assignmentComitHash", DUMMY_COMMIT_HASH));
        dockerClientTestService.mockInputStreamReturnedFromContainer(dockerClient, LOCAL_CI_WORKING_DIRECTORY + "/testing-dir/.git/refs/heads/[^/]+",
                Map.of("testsCommitHash", DUMMY_COMMIT_HASH), Map.of("testsCommitHash", DUMMY_COMMIT_HASH));

        dockerClientTestService.mockInspectImage(dockerClient);

        // Mock dockerClient.copyArchiveFromContainerCmd() such that it returns the XMLs containing the test results.
        // Mock the results for the template repository build and for the solution repository build that will both be triggered as a result of creating the exercise.
        Map<String, String> templateBuildTestResults = dockerClientTestService.createMapFromTestResultsFolder(ALL_FAIL_TEST_RESULTS_PATH);
        Map<String, String> solutionBuildTestResults = dockerClientTestService.createMapFromTestResultsFolder(ALL_SUCCEED_TEST_RESULTS_PATH);
        dockerClientTestService.mockInputStreamReturnedFromContainer(dockerClient, LOCAL_CI_WORKING_DIRECTORY + LOCAL_CI_RESULTS_DIRECTORY, templateBuildTestResults,
                solutionBuildTestResults);

        ProgrammingExercise exerciseToBeImported = ProgrammingExerciseFactory.generateToBeImportedProgrammingExercise("ImportTitle", "imported", programmingExercise,
                courseUtilService.addEmptyCourse());

        // Import the exercise and load all referenced entities
        var params = new LinkedMultiValueMap<String, String>();
        params.add("recreateBuildPlans", "true");
        exerciseToBeImported.setChannelName("testchannel-pe-imported");
        exerciseToBeImported.setCompetencyLinks(Set.of(new CompetencyExerciseLink(competency, exerciseToBeImported, 1)));
        exerciseToBeImported.getCompetencyLinks().forEach(link -> link.getCompetency().setCourse(null));

        var importedExercise = request.postWithResponseBody("/api/programming/programming-exercises/import/" + programmingExercise.getId(), exerciseToBeImported,
                ProgrammingExercise.class, params, HttpStatus.OK);

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
        verify(competencyProgressApi).updateProgressByLearningObjectAsync(eq(importedExercise));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importFromFile_missingExerciseDetailsJson_badRequest() throws Exception {
        programmingExerciseTestService.importFromFile_missingExerciseDetailsJson_badRequest();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importFromFile_fileNoZip_badRequest() throws Exception {
        programmingExerciseTestService.importFromFile_fileNoZip_badRequest();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importFromFile_tutor_forbidden() throws Exception {
        programmingExerciseTestService.importFromFile_tutor_forbidden();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importFromFile_missingRepository_BadRequest() throws Exception {
        programmingExerciseTestService.importFromFile_missingRepository_BadRequest();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importFromFile_exception_DirectoryDeleted() throws Exception {
        programmingExerciseTestService.importFromFile_exception_DirectoryDeleted();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createProgrammingExercise_failToCreateProjectInCi() throws Exception {
        programmingExerciseTestService.createProgrammingExercise_failToCreateProjectInCi();
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ArgumentsSource(InvalidExamExerciseDatesArgumentProvider.class)
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createProgrammingExerciseForExam_invalidExercise_dates(InvalidExamExerciseDateConfiguration dates) throws Exception {
        programmingExerciseTestService.createProgrammingExerciseForExam_invalidExercise_dates(dates);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createProgrammingExerciseForExam_DatesSet() throws Exception {
        programmingExerciseTestService.createProgrammingExerciseForExam_DatesSet();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createProgrammingExercise_setInvalidExampleSolutionPublicationDate_badRequest() throws Exception {
        programmingExerciseTestService.createProgrammingExercise_setInvalidExampleSolutionPublicationDate_badRequest();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importFromFile_validImportZip_changeTitle_success() throws Exception {
        String uniqueSuffix = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase();
        var resource = new ClassPathResource("test-data/import-from-file/valid-import.zip");
        ZipInputStream zipInputStream = new ZipInputStream(resource.getInputStream());
        String detailsJsonString = null;
        ZipEntry entry;
        while ((entry = zipInputStream.getNextEntry()) != null) {
            if (entry.getName().endsWith(".json")) {
                // Read the JSON file as a String
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int len;
                while ((len = zipInputStream.read(buffer)) > 0) {
                    baos.write(buffer, 0, len);
                }
                detailsJsonString = baos.toString(StandardCharsets.UTF_8);
                break;
            }
        }
        zipInputStream.close();
        assertThat(detailsJsonString).isNotNull();
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.findAndRegisterModules();
        ProgrammingExercise parsedExercise = objectMapper.readValue(detailsJsonString, ProgrammingExercise.class);
        if (parsedExercise.getBuildConfig() == null) {
            parsedExercise.setBuildConfig(new de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig());
        }
        // Change the title and short name to unique values
        String oldTitle = parsedExercise.getTitle();
        String newTitle = "TITLE" + uniqueSuffix;
        String newShortName = "SHORT" + uniqueSuffix;
        parsedExercise.setTitle(newTitle);
        parsedExercise.setShortName(newShortName);
        parsedExercise.setCourse(course);
        parsedExercise.setId(null);
        parsedExercise.setChannelName("testchannel-pe-imported");
        parsedExercise.forceNewProjectKey();
        // Prepare the file for import
        MockMultipartFile file = new MockMultipartFile("file", "test.zip", "application/zip", resource.getInputStream());
        // Count old title occurrences in the original zip
        int oldTitleCountInZip = 0;
        try (ZipInputStream zipIn = new ZipInputStream(new FileInputStream(resource.getFile()))) {
            ZipEntry zipEntry;
            while ((zipEntry = zipIn.getNextEntry()) != null) {
                if (!zipEntry.isDirectory() && zipEntry.getName().endsWith(".zip")) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    byte[] buf = new byte[1024];
                    int n;
                    while ((n = zipIn.read(buf)) > 0) {
                        baos.write(buf, 0, n);
                    }
                    String content = baos.toString(StandardCharsets.UTF_8);
                    int idx = 0;
                    while ((idx = content.indexOf(oldTitle, idx)) != -1) {
                        oldTitleCountInZip++;
                        idx += oldTitle.length();
                    }
                }
            }
        }
        ProgrammingExercise importedExercise = request.postWithMultipartFile("/api/programming/courses/" + course.getId() + "/programming-exercises/import-from-file",
                parsedExercise, "programmingExercise", file, ProgrammingExercise.class, HttpStatus.OK);

        assertThat(importedExercise).isNotNull();
        assertThat(importedExercise.getTitle()).isEqualTo(newTitle);
        assertThat(importedExercise.getProgrammingLanguage()).isEqualTo(parsedExercise.getProgrammingLanguage());
        assertThat(importedExercise.getCourseViaExerciseGroupOrCourseMember()).isEqualTo(course);

        String repoClonePath = System.getProperty("artemis.repo-clone-path", "repos");
        String projectKey = parsedExercise.getProjectKey();
        String[] repoDirs = { projectKey + "-exercise", projectKey + "-solution", projectKey + "-tests" };
        int newTitleCount = 0;
        int oldTitleCount = 0;
        for (String repoDir : repoDirs) {
            Path repoPath = Paths.get(repoClonePath, projectKey, repoDir.toLowerCase());
            if (!Files.exists(repoPath))
                continue;
            List<Path> files = new ArrayList<>();
            Files.walk(repoPath).filter(Files::isRegularFile).forEach(files::add);
            for (Path filePath : files) {
                String content = new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8);
                int idx = 0;
                while ((idx = content.indexOf(newTitle, idx)) != -1) {
                    newTitleCount++;
                    idx += newTitle.length();
                }
                idx = 0;
                while ((idx = content.indexOf(oldTitle, idx)) != -1) {
                    oldTitleCount++;
                    idx += oldTitle.length();
                }
            }
        }
        assertThat(newTitleCount).isEqualTo(oldTitleCountInZip);
        assertThat(oldTitleCount).isZero();
    }

    @Nested
    class TestGetCheckoutDirectories {

        @Test
        @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
        void testWithValidProgrammingLanguage() throws Exception {
            CheckoutDirectoriesDTO checkoutDirectoryDTO = request.get("/api/programming/programming-exercises/repository-checkout-directories?programmingLanguage=JAVA",
                    HttpStatus.OK, CheckoutDirectoriesDTO.class);

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
            request.get("/api/programming/programming-exercises/repository-checkout-directories?programmingLanguage=languageThatDoesNotExist", HttpStatus.BAD_REQUEST,
                    CheckoutDirectoriesDTO.class);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
        void testAccessForbidden() throws Exception {
            request.get("/api/programming/programming-exercises/repository-checkout-directories?programmingLanguage=JAVA", HttpStatus.FORBIDDEN, CheckoutDirectoriesDTO.class);
        }
    }
}
