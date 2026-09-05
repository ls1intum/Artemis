package de.tum.cit.aet.artemis.localci.service;

import static de.tum.cit.aet.artemis.core.config.Constants.LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY;
import static de.tum.cit.aet.artemis.core.config.Constants.LOCAL_CI_RESULTS_DIRECTORY;
import static de.tum.cit.aet.artemis.core.config.Constants.MAX_BUILD_PLAN_CONFIGURATION_LENGTH;
import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.assertExerciseNotInWeaviate;
import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.assertProgrammingExerciseExistsInWeaviate;
import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.queryExerciseProperties;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.nio.file.Path;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.atlas.domain.LearningObject;
import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyExerciseLink;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.repository.conversation.ChannelRepository;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exam.util.InvalidExamExerciseDatesArgumentProvider;
import de.tum.cit.aet.artemis.exam.util.InvalidExamExerciseDatesArgumentProvider.InvalidExamExerciseDateConfiguration;
import de.tum.cit.aet.artemis.exercise.domain.InitializationState;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;
import de.tum.cit.aet.artemis.globalsearch.dto.searchableentity.ExerciseSearchableEntityDTO;
import de.tum.cit.aet.artemis.globalsearch.service.SearchableEntityWeaviateService;
import de.tum.cit.aet.artemis.globalsearch.service.WeaviateService;
import de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.localvc.util.LocalVCRepositoryTestService;
import de.tum.cit.aet.artemis.localvc.util.LocalVCTestRepository;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationLocalCILocalVCTestBase;
import de.tum.cit.aet.artemis.programming.domain.AuxiliaryRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.SolutionProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.TemplateProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.build.BuildPhaseCondition;
import de.tum.cit.aet.artemis.programming.dto.BuildPhaseDTO;
import de.tum.cit.aet.artemis.programming.dto.BuildPlanPhasesDTO;
import de.tum.cit.aet.artemis.programming.dto.CheckoutDirectoriesDTO;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseFactory;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseImportTestService;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseImportTestService.ImportFileResult;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseTestService;
import de.tum.cit.aet.artemis.programming.util.RepositoryExportTestUtil;

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

    private static final String POM_XML = "pom.xml";

    private static final String PACKAGE_NAME_FOLDER_PLACEHOLDER = "${packageNameFolder}";

    private static final String PACKAGE_NAME_PLACEHOLDER = "${packageName}";

    /** The exercises created through the setup endpoint, whose repositories the server owns and this test therefore has to clean up itself. */
    private final List<ProgrammingExercise> createdExercisesToCleanUp = new ArrayList<>();

    private Course course;

    private ProgrammingExercise programmingExercise;

    private LocalVCTestRepository templateRepository;

    private LocalVCTestRepository solutionRepository;

    private LocalVCTestRepository testsRepository;

    private LocalVCTestRepository assignmentRepository;

    private Competency competency;

    @Value("${artemis.repo-clone-path}")
    private String repoClonePath;

    @Autowired
    private LocalVCRepositoryTestService localVCRepositoryTestService;

    @Autowired
    private ProgrammingExerciseTestService programmingExerciseTestService;

    @Autowired
    private ProgrammingExerciseImportTestService programmingExerciseImportTestService;

    @Autowired(required = false)
    private WeaviateService weaviateService;

    @Autowired(required = false)
    private SearchableEntityWeaviateService searchableEntityWeaviateService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private ChannelRepository channelRepository;

    @BeforeAll
    void setupAll() {
        CredentialsProvider.setDefault(new UsernamePasswordCredentialsProvider(localVCUsername, localVCPassword));
    }

    @BeforeEach
    void setup() throws Exception {
        programmingExerciseTestService.setupTestUsers(TEST_PREFIX, 0, 0, 0, 0);

        course = programmingExerciseUtilService.addEnrolledCourseWithOneProgrammingExercise(TEST_PREFIX);
        programmingExercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        String projectKey = programmingExercise.getProjectKey();
        programmingExercise.setProjectType(ProjectType.PLAIN_GRADLE);
        programmingExercise.setTestRepositoryUri(localVCBaseUri + "/git/" + projectKey + "/" + projectKey.toLowerCase() + "-tests.git");
        programmingExerciseRepository.save(programmingExercise);
        programmingExercise = programmingExerciseRepository.findWithAllParticipationsAndBuildConfigById(programmingExercise.getId()).orElseThrow();

        // Set the correct repository URIs for the template and the solution participation.
        String templateRepositorySlug = projectKey.toLowerCase() + "-exercise";
        TemplateProgrammingExerciseParticipation templateParticipation = programmingExercise.getTemplateParticipation();
        templateParticipation.setRepositoryUri(localVCBaseUri + "/git/" + projectKey + "/" + templateRepositorySlug + ".git");
        templateProgrammingExerciseParticipationRepository.save(templateParticipation);
        String solutionRepositorySlug = projectKey.toLowerCase() + "-solution";
        SolutionProgrammingExerciseParticipation solutionParticipation = programmingExercise.getSolutionParticipation();
        solutionParticipation.setRepositoryUri(localVCBaseUri + "/git/" + projectKey + "/" + solutionRepositorySlug + ".git");
        solutionProgrammingExerciseParticipationRepository.save(solutionParticipation);

        String assignmentRepositorySlug = projectKey.toLowerCase() + "-" + TEST_PREFIX + "student1";

        // Add a participation for student1.
        ProgrammingExerciseStudentParticipation studentParticipation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise,
                TEST_PREFIX + "student1");
        studentParticipation.setRepositoryUri((localVCBaseUri + "/git/%s/%s.git").formatted(projectKey, assignmentRepositorySlug));
        studentParticipation.setBranch(defaultBranch);
        programmingExerciseStudentParticipationRepository.save(studentParticipation);

        // Prepare the repositories.
        templateRepository = localVCLocalCITestService.createRepositoryWithWorkingCopy(projectKey, templateRepositorySlug);
        testsRepository = localVCLocalCITestService.createRepositoryWithWorkingCopy(projectKey, projectKey.toLowerCase() + "-tests");
        solutionRepository = localVCLocalCITestService.createRepositoryWithWorkingCopy(projectKey, solutionRepositorySlug);
        assignmentRepository = localVCLocalCITestService.createRepositoryWithWorkingCopy(projectKey, assignmentRepositorySlug);

        // Check that the repository folders were created in the file system for all base repositories.
        localVCLocalCITestService.verifyRepositoryFoldersExist(programmingExercise, localVCBasePath);

        competency = competencyUtilService.createCompetency(course);

        programmingExerciseTestService.setup(this, versionControlService);
    }

    @Override
    protected String getTestPrefix() {
        return TEST_PREFIX;
    }

    @AfterEach
    void tearDown() throws Exception {
        for (ProgrammingExercise createdExercise : createdExercisesToCleanUp) {
            RepositoryExportTestUtil.deleteLocalVcProjectIfPresent(localVCBasePath, createdExercise.getProjectKey());
        }
        createdExercisesToCleanUp.clear();
        templateRepository.deleteWorkingCopy();
        solutionRepository.deleteWorkingCopy();
        testsRepository.deleteWorkingCopy();
        assignmentRepository.deleteWorkingCopy();
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
        dockerClientTestService.mockInputStreamReturnedFromContainer(dockerClient, LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY + "/testing-dir/assignment/.git/refs/heads/[^/]+",
                Map.of("assignmentCommitHash", DUMMY_COMMIT_HASH), Map.of("assignmentCommitHash", DUMMY_COMMIT_HASH));
        dockerClientTestService.mockInputStreamReturnedFromContainer(dockerClient, LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY + "/testing-dir/.git/refs/heads/[^/]+",
                Map.of("testsCommitHash", DUMMY_COMMIT_HASH), Map.of("testsCommitHash", DUMMY_COMMIT_HASH));

        dockerClientTestService.mockInspectImage(dockerClient);

        // Mock dockerClient.copyArchiveFromContainerCmd() such that it returns the XMLs containing the test results.
        // Mock the results for the template repository build and for the solution repository build that will both be triggered as a result of creating the exercise.
        Map<String, String> templateBuildTestResults = dockerClientTestService.createMapFromTestResultsFolder(ALL_FAIL_TEST_RESULTS_PATH);
        Map<String, String> solutionBuildTestResults = dockerClientTestService.createMapFromTestResultsFolder(ALL_SUCCEED_TEST_RESULTS_PATH);
        dockerClientTestService.mockInputStreamReturnedFromContainer(dockerClient, LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY + LOCAL_CI_RESULTS_DIRECTORY,
                templateBuildTestResults, solutionBuildTestResults);
        newExercise.setChannelName("testchannelname-pe");
        ProgrammingExercise createdExercise = request.postWithResponseBody("/api/programming/programming-exercises/setup", newExercise, ProgrammingExercise.class,
                HttpStatus.CREATED);

        // Check that the repository folders were created in the file system for the template, solution, and tests repository.
        localVCLocalCITestService.verifyRepositoryFoldersExist(createdExercise, localVCBasePath);

        // Also check that the template and solution repositories were built successfully.
        localVCLocalCITestService.testLatestSubmission(createdExercise.getTemplateParticipation().getId(), null, 0, false);
        localVCLocalCITestService.testLatestSubmission(createdExercise.getSolutionParticipation().getId(), null, 13, false);

        verify(competencyProgressApi).updateProgressByLearningObjectAsync(eq(createdExercise));

        assertProgrammingExerciseExistsInWeaviate(weaviateService, createdExercise);
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
        ZonedDateTime originalReleaseDate = programmingExercise.getReleaseDate();

        // Pre-populate Weaviate with the exercise to avoid race condition on first insert
        // This ensures we're actually testing the UPDATE path, not the INSERT path
        if (searchableEntityWeaviateService != null && weaviateService != null) {
            searchableEntityWeaviateService.upsertExerciseAsync(ExerciseSearchableEntityDTO.fromExercise(programmingExercise));
            // Wait for initial insert to complete before proceeding with update
            await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
                var properties = queryExerciseProperties(weaviateService, programmingExercise.getId());
                assertThat(properties).as("Exercise should be initially present in Weaviate before update").isNotNull();
                Object releaseDateObj = properties.get(SearchableEntitySchema.Properties.RELEASE_DATE);
                assertThat(releaseDateObj).as("Initial release date should be set in Weaviate").isNotNull();
            });
        }

        ZonedDateTime newReleaseDate = ZonedDateTime.now().plusHours(1);
        programmingExercise.setReleaseDate(newReleaseDate);
        programmingExercise.setCompetencyLinks(Set.of(new CompetencyExerciseLink(competency, programmingExercise, 1)));
        programmingExercise.getCompetencyLinks().forEach(link -> link.getCompetency().setCourse(null));

        ProgrammingExercise updatedExercise = request.putWithResponseBody("/api/programming/programming-exercises",
                de.tum.cit.aet.artemis.programming.dto.UpdateProgrammingExerciseDTO.of(programmingExercise), ProgrammingExercise.class, HttpStatus.OK);

        // Compare as instants because PostgreSQL stores timestamps as UTC and the
        // original timezone offset is not preserved through the database round-trip.
        assertThat(updatedExercise.getReleaseDate().toInstant()).isEqualTo(newReleaseDate.toInstant());
        verify(competencyProgressApi, timeout(1000).times(1)).updateProgressForUpdatedLearningObjectAsyncWithOriginalCompetencyIds(eq(Set.of()), any());

        if (!WeaviateTestUtil.shouldSkipWeaviateAssertions(weaviateService)) {
            await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
                var weaviateProperties = queryExerciseProperties(weaviateService, updatedExercise.getId());
                assertThat(weaviateProperties).as("Exercise properties should exist in Weaviate after update").isNotNull();
                assertThat(weaviateProperties.get(SearchableEntitySchema.Properties.TITLE)).isEqualTo(updatedExercise.getTitle());
                assertThat(((Number) weaviateProperties.get(SearchableEntitySchema.Properties.ENTITY_ID)).longValue()).isEqualTo(updatedExercise.getId());
                // Verify that the release date was actually updated in Weaviate
                Object releaseDateObj = weaviateProperties.get(SearchableEntitySchema.Properties.RELEASE_DATE);
                assertThat(releaseDateObj).as("Release date should be updated in Weaviate").isNotNull();
                ZonedDateTime weaviateReleaseDate = ZonedDateTime.parse(releaseDateObj.toString());
                // Compare as instants with a small tolerance because Weaviate may not preserve
                // sub-millisecond precision through the serialization round-trip.
                assertThat(weaviateReleaseDate.toInstant()).isCloseTo(newReleaseDate.toInstant(), within(100, java.time.temporal.ChronoUnit.MILLIS));
                assertThat(weaviateReleaseDate.toInstant()).isNotEqualTo(originalReleaseDate.toInstant());
            });
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateProgrammingExercise_usesOriginalCompetenciesForProgressUpdate() throws Exception {
        Competency replacementCompetency = competencyUtilService.createCompetency(course);

        programmingExercise.setCompetencyLinks(Set.of(new CompetencyExerciseLink(competency, programmingExercise, 1)));
        programmingExerciseRepository.saveAndFlush(programmingExercise);

        AtomicReference<Set<Long>> originalCompetencyIds = new AtomicReference<>();
        AtomicReference<Set<Long>> updatedCompetencyIds = new AtomicReference<>();
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Set<Long> originalIds = invocation.getArgument(0);
            LearningObject updatedLearningObject = invocation.getArgument(1);

            originalCompetencyIds.set(Set.copyOf(originalIds));
            updatedCompetencyIds.set(updatedLearningObject.getCompetencyLinks().stream().map(link -> link.getCompetency().getId()).collect(Collectors.toSet()));
            return null;
        }).when(competencyProgressApi).updateProgressForUpdatedLearningObjectAsyncWithOriginalCompetencyIds(any(), any());

        programmingExercise.setCompetencyLinks(Set.of(new CompetencyExerciseLink(replacementCompetency, programmingExercise, 1)));

        request.putWithResponseBody("/api/programming/programming-exercises", de.tum.cit.aet.artemis.programming.dto.UpdateProgrammingExerciseDTO.of(programmingExercise),
                ProgrammingExercise.class, HttpStatus.OK);

        assertThat(originalCompetencyIds.get()).containsExactly(competency.getId());
        assertThat(updatedCompetencyIds.get()).containsExactly(replacementCompetency.getId());
        verify(competencyProgressApi, timeout(1000).times(1)).updateProgressForUpdatedLearningObjectAsyncWithOriginalCompetencyIds(any(), any());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateProgrammingExercise_withCompetencyFromDifferentCourse_badRequest() throws Exception {
        Course otherCourse = courseUtilService.createCourse();
        Competency foreignCompetency = competencyUtilService.createCompetency(otherCourse);

        programmingExercise.setCompetencyLinks(Set.of(new CompetencyExerciseLink(foreignCompetency, programmingExercise, 1)));

        request.putWithResponseBody("/api/programming/programming-exercises", de.tum.cit.aet.artemis.programming.dto.UpdateProgrammingExerciseDTO.of(programmingExercise),
                ProgrammingExercise.class, HttpStatus.BAD_REQUEST);
    }

    // Note: testUpdateProgrammingExercise_templateRepositoryUriIsInvalid was removed because
    // UpdateProgrammingExerciseDTO intentionally doesn't include templateRepositoryUri.
    // Repository URIs are immutable after exercise creation and cannot be modified through the update endpoint.

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateProgrammingExercise_invalidBuildPhaseName() throws Exception {
        programmingExercise.getBuildConfig().setBuildPlanConfiguration("""
                {
                  "phases": [
                    {
                      "name": "invalid phase",
                      "script": "echo test",
                      "condition": "ALWAYS",
                      "forceRun": false,
                      "resultPaths": []
                    }
                  ]
                }
                """);

        request.put("/api/programming/programming-exercises", programmingExercise, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateProgrammingExercise_duplicateBuildPhaseNames_caseInsensitive() throws Exception {
        programmingExercise.getBuildConfig().setBuildPlanConfiguration("""
                {
                  "phases": [
                    {
                      "name": "Build",
                      "script": "echo build",
                      "condition": "ALWAYS",
                      "forceRun": false,
                      "resultPaths": []
                    },
                    {
                      "name": "build",
                      "script": "echo test",
                      "condition": "ALWAYS",
                      "forceRun": false,
                      "resultPaths": []
                    }
                  ]
                }
                """);

        request.put("/api/programming/programming-exercises", programmingExercise, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateProgrammingExercise_reservedBuildPhaseName_caseInsensitive() throws Exception {
        programmingExercise.getBuildConfig().setBuildPlanConfiguration("""
                {
                  "phases": [
                    {
                      "name": "main",
                      "script": "echo build",
                      "condition": "ALWAYS",
                      "forceRun": false,
                      "resultPaths": []
                    }
                  ]
                }
                """);

        request.put("/api/programming/programming-exercises", programmingExercise, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteProgrammingExercise() throws Exception {
        programmingExercise.setCompetencyLinks(Set.of(new CompetencyExerciseLink(competency, programmingExercise, 1)));
        programmingExerciseRepository.save(programmingExercise);
        long exerciseId = programmingExercise.getId();

        // Delete the exercise
        var params = new LinkedMultiValueMap<String, String>();
        params.add("deleteStudentReposBuildPlans", "true");
        params.add("deleteBaseReposBuildPlans", "true");
        request.delete("/api/programming/programming-exercises/" + exerciseId, HttpStatus.OK, params);

        // Assert that the repository folders do not exist anymore.
        LocalVCRepositoryUri templateRepositoryUri = new LocalVCRepositoryUri(programmingExercise.getTemplateRepositoryUri());
        assertThat(templateRepositoryUri.getLocalRepositoryPath(localVCBasePath)).doesNotExist();
        LocalVCRepositoryUri solutionRepositoryUri = new LocalVCRepositoryUri(programmingExercise.getSolutionRepositoryUri());
        assertThat(solutionRepositoryUri.getLocalRepositoryPath(localVCBasePath)).doesNotExist();
        LocalVCRepositoryUri testsRepositoryUri = new LocalVCRepositoryUri(programmingExercise.getTestRepositoryUri());
        assertThat(testsRepositoryUri.getLocalRepositoryPath(localVCBasePath)).doesNotExist();
        verify(competencyProgressApi).updateProgressByCompetencyAsync(eq(competency));

        assertExerciseNotInWeaviate(weaviateService, exerciseId);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testImportProgrammingExercise() throws Exception {
        // Mock dockerClient.copyArchiveFromContainerCmd() such that it returns a dummy commitHash for both the assignment and the test repository.
        // Note: The stub needs to receive the same object twice because there are two requests to the same method (one for the template participation and one for the solution
        // participation).
        // Usually, specifying one doReturn() is enough to make the stub return the same object on every subsequent call.
        // However, in this case we have it return an InputStream, which will be consumed after returning it the first time, so we need to create two separate ones.
        dockerClientTestService.mockInputStreamReturnedFromContainer(dockerClient, LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY + "/testing-dir/assignment/.git/refs/heads/[^/]+",
                Map.of("assignmentComitHash", DUMMY_COMMIT_HASH), Map.of("assignmentComitHash", DUMMY_COMMIT_HASH));
        dockerClientTestService.mockInputStreamReturnedFromContainer(dockerClient, LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY + "/testing-dir/.git/refs/heads/[^/]+",
                Map.of("testsCommitHash", DUMMY_COMMIT_HASH), Map.of("testsCommitHash", DUMMY_COMMIT_HASH));

        dockerClientTestService.mockInspectImage(dockerClient);

        // Mock dockerClient.copyArchiveFromContainerCmd() such that it returns the XMLs containing the test results.
        // Mock the results for the template repository build and for the solution repository build that will both be triggered as a result of creating the exercise.
        Map<String, String> templateBuildTestResults = dockerClientTestService.createMapFromTestResultsFolder(ALL_FAIL_TEST_RESULTS_PATH);
        Map<String, String> solutionBuildTestResults = dockerClientTestService.createMapFromTestResultsFolder(ALL_SUCCEED_TEST_RESULTS_PATH);
        dockerClientTestService.mockInputStreamReturnedFromContainer(dockerClient, LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY + LOCAL_CI_RESULTS_DIRECTORY,
                templateBuildTestResults, solutionBuildTestResults);

        programmingExercise.setGradingCriteria(ProgrammingExerciseFactory.generateGradingCriteria(programmingExercise));
        programmingExercise = programmingExerciseRepository.save(programmingExercise);
        programmingExercise = programmingExerciseRepository.findWithPlagiarismDetectionConfigTeamConfigBuildConfigAndGradingCriteriaById(programmingExercise.getId()).orElseThrow();
        ProgrammingExercise exerciseToBeImported = ProgrammingExerciseFactory.generateToBeImportedProgrammingExercise("ImportTitle", "imported", programmingExercise,
                courseUtilService.addEnrolledEmptyCourse(TEST_PREFIX));

        // Import the exercise and load all referenced entities
        var params = new LinkedMultiValueMap<String, String>();
        params.add("recreateBuildPlans", "true");
        exerciseToBeImported.setChannelName("testchannel-pe-imported");
        exerciseToBeImported.setCompetencyLinks(Set.of(new CompetencyExerciseLink(competency, exerciseToBeImported, 1)));
        exerciseToBeImported.getCompetencyLinks().forEach(link -> link.getCompetency().setCourse(null));

        var importedExercise = request.postWithResponseBody("/api/programming/programming-exercises/import?sourceExerciseId=" + programmingExercise.getId(), exerciseToBeImported,
                ProgrammingExercise.class, params, HttpStatus.OK);

        // Assert that the repositories were correctly created for the imported exercise.
        ProgrammingExercise importedExerciseWithParticipations = programmingExerciseRepository.findWithAllParticipationsAndBuildConfigById(importedExercise.getId()).orElseThrow();
        localVCLocalCITestService.verifyRepositoryFoldersExist(importedExerciseWithParticipations, localVCBasePath);
        assertThat(importedExercise.getGradingCriteria()).hasSize(1);

        // Also check that the template and solution repositories were built successfully.
        TemplateProgrammingExerciseParticipation templateParticipation = templateProgrammingExerciseParticipationRepository.findByProgrammingExerciseId(importedExercise.getId())
                .orElseThrow();
        SolutionProgrammingExerciseParticipation solutionParticipation = solutionProgrammingExerciseParticipationRepository.findByProgrammingExerciseId(importedExercise.getId())
                .orElseThrow();
        // Verifying the build was triggered is enough.
        // The actual test results are not important for this test and only lead to a lot of flakiness
        verify(localCITriggerService, timeout(5000).times(1)).triggerBuild(eq(templateParticipation));
        verify(localCITriggerService, timeout(5000).times(1)).triggerBuild(eq(solutionParticipation));
        verify(competencyProgressApi).updateProgressByLearningObjectAsync(eq(importedExercise));

        assertProgrammingExerciseExistsInWeaviate(weaviateService, importedExercise);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testImportProgrammingExercise_initializesParticipationsAndClonesReferences() throws Exception {
        // Stub the LocalCI build container reads. The git repositories themselves are real (LocalVC) and are not mocked.
        dockerClientTestService.mockInputStreamReturnedFromContainer(dockerClient, LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY + "/testing-dir/assignment/.git/refs/heads/[^/]+",
                Map.of("assignmentComitHash", DUMMY_COMMIT_HASH), Map.of("assignmentComitHash", DUMMY_COMMIT_HASH));
        dockerClientTestService.mockInputStreamReturnedFromContainer(dockerClient, LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY + "/testing-dir/.git/refs/heads/[^/]+",
                Map.of("testsCommitHash", DUMMY_COMMIT_HASH), Map.of("testsCommitHash", DUMMY_COMMIT_HASH));
        dockerClientTestService.mockInspectImage(dockerClient);
        Map<String, String> templateBuildTestResults = dockerClientTestService.createMapFromTestResultsFolder(ALL_FAIL_TEST_RESULTS_PATH);
        Map<String, String> solutionBuildTestResults = dockerClientTestService.createMapFromTestResultsFolder(ALL_SUCCEED_TEST_RESULTS_PATH);
        dockerClientTestService.mockInputStreamReturnedFromContainer(dockerClient, LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY + LOCAL_CI_RESULTS_DIRECTORY,
                templateBuildTestResults, solutionBuildTestResults);

        final long sourceBuildConfigId = programmingExercise.getBuildConfig().getId();
        programmingExercise.setGradingCriteria(ProgrammingExerciseFactory.generateGradingCriteria(programmingExercise));
        programmingExercise = programmingExerciseRepository.save(programmingExercise);
        programmingExercise = programmingExerciseRepository.findWithPlagiarismDetectionConfigTeamConfigBuildConfigAndGradingCriteriaById(programmingExercise.getId()).orElseThrow();

        ProgrammingExercise exerciseToBeImported = ProgrammingExerciseFactory.generateToBeImportedProgrammingExercise("InitTitle", "initimp", programmingExercise,
                courseUtilService.addEnrolledEmptyCourse(TEST_PREFIX));
        exerciseToBeImported.setChannelName("testchannel-pe-init");
        var params = new LinkedMultiValueMap<String, String>();
        params.add("recreateBuildPlans", "true");

        var importedExercise = request.postWithResponseBody("/api/programming/programming-exercises/import?sourceExerciseId=" + programmingExercise.getId(), exerciseToBeImported,
                ProgrammingExercise.class, params, HttpStatus.OK);

        // The template and solution participations are persisted and INITIALIZED even though the import no longer runs in
        // a surrounding transaction (they are set up and saved explicitly). This is the core guarantee of the refactor.
        TemplateProgrammingExerciseParticipation templateParticipation = templateProgrammingExerciseParticipationRepository.findByProgrammingExerciseId(importedExercise.getId())
                .orElseThrow();
        SolutionProgrammingExerciseParticipation solutionParticipation = solutionProgrammingExerciseParticipationRepository.findByProgrammingExerciseId(importedExercise.getId())
                .orElseThrow();
        assertThat(templateParticipation.getInitializationState()).isEqualTo(InitializationState.INITIALIZED);
        assertThat(solutionParticipation.getInitializationState()).isEqualTo(InitializationState.INITIALIZED);

        // The grading criteria and build config are deep-copied from the source: the grading criteria are preserved and
        // the build config is a fresh entity (different id).
        ProgrammingExercise importedWithReferences = programmingExerciseRepository
                .findWithPlagiarismDetectionConfigTeamConfigBuildConfigAndGradingCriteriaById(importedExercise.getId()).orElseThrow();
        assertThat(importedWithReferences.getGradingCriteria()).hasSize(1);
        assertThat(importedWithReferences.getBuildConfig().getId()).isNotEqualTo(sourceBuildConfigId);

        // The channel is created with the name the client supplied. The channel name is transient, so it does not survive
        // the re-fetch of the imported exercise and has to be captured before it (regression guard).
        assertThat(channelRepository.findChannelByExerciseId(importedExercise.getId())).isNotNull().extracting(Channel::getName).isEqualTo("testchannel-pe-init");

        // The repositories were really created on the local VCS (not mocked).
        localVCLocalCITestService.verifyRepositoryFoldersExist(programmingExerciseRepository.findWithAllParticipationsAndBuildConfigById(importedExercise.getId()).orElseThrow(),
                localVCBasePath);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testImportProgrammingExercise_withAfterDueDatePhase_computesBuildAndTestDate() throws Exception {
        // Mock dockerClient.copyArchiveFromContainerCmd()
        dockerClientTestService.mockInputStreamReturnedFromContainer(dockerClient, LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY + "/testing-dir/assignment/.git/refs/heads/[^/]+",
                Map.of("assignmentComitHash", DUMMY_COMMIT_HASH), Map.of("assignmentComitHash", DUMMY_COMMIT_HASH));
        dockerClientTestService.mockInputStreamReturnedFromContainer(dockerClient, LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY + "/testing-dir/.git/refs/heads/[^/]+",
                Map.of("testsCommitHash", DUMMY_COMMIT_HASH), Map.of("testsCommitHash", DUMMY_COMMIT_HASH));
        dockerClientTestService.mockInspectImage(dockerClient);

        Map<String, String> templateBuildTestResults = dockerClientTestService.createMapFromTestResultsFolder(ALL_FAIL_TEST_RESULTS_PATH);
        Map<String, String> solutionBuildTestResults = dockerClientTestService.createMapFromTestResultsFolder(ALL_SUCCEED_TEST_RESULTS_PATH);
        dockerClientTestService.mockInputStreamReturnedFromContainer(dockerClient, LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY + LOCAL_CI_RESULTS_DIRECTORY,
                templateBuildTestResults, solutionBuildTestResults);

        // Setup source exercise with an AFTER_DUE_DATE build phase
        ZonedDateTime dueDate = ZonedDateTime.now().plusDays(2);
        programmingExercise.setDueDate(dueDate);
        programmingExercise.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);
        programmingExercise.setGradingCriteria(ProgrammingExerciseFactory.generateGradingCriteria(programmingExercise));

        var phase = new BuildPhaseDTO("test", "echo test", BuildPhaseCondition.AFTER_DUE_DATE, false, List.of("build/test-results/*.xml"));
        var buildConfig = programmingExercise.getBuildConfig();
        if (buildConfig == null) {
            buildConfig = new ProgrammingExerciseBuildConfig();
            buildConfig.setProgrammingExercise(programmingExercise);
            programmingExercise.setBuildConfig(buildConfig);
        }
        buildConfig.setBuildPlanConfiguration(new BuildPlanPhasesDTO(List.of(phase), "ghcr.io/example-image").toBuildPlanConfiguration());

        programmingExercise = programmingExerciseRepository.save(programmingExercise);
        programmingExercise = programmingExerciseRepository.findWithPlagiarismDetectionConfigTeamConfigBuildConfigAndGradingCriteriaById(programmingExercise.getId()).orElseThrow();

        ProgrammingExercise exerciseToBeImported = ProgrammingExerciseFactory.generateToBeImportedProgrammingExercise("ImportADDTitle", "addimport", programmingExercise,
                courseUtilService.addEnrolledEmptyCourse(TEST_PREFIX));
        exerciseToBeImported.getBuildConfig().setBuildPlanConfiguration(new BuildPlanPhasesDTO(List.of(phase), "ghcr.io/example-image").toBuildPlanConfiguration());
        // Explicitly set the field to null to trigger computation on the server
        exerciseToBeImported.setBuildAndTestStudentSubmissionsAfterDueDate(null);

        var params = new LinkedMultiValueMap<String, String>();
        params.add("recreateBuildPlans", "false");
        exerciseToBeImported.setChannelName("testchannel-pe-addimport");
        exerciseToBeImported.setCompetencyLinks(Set.of(new CompetencyExerciseLink(competency, exerciseToBeImported, 1)));
        exerciseToBeImported.getCompetencyLinks().forEach(link -> link.getCompetency().setCourse(null));

        var importedExercise = request.postWithResponseBody("/api/programming/programming-exercises/import/" + programmingExercise.getId(), exerciseToBeImported,
                ProgrammingExercise.class, params, HttpStatus.OK);

        // Verify that the Build And Test Date was correctly computed and persisted
        ProgrammingExercise importedExerciseWithParticipations = programmingExerciseRepository.findWithAllParticipationsAndBuildConfigById(importedExercise.getId()).orElseThrow();

        assertThat(importedExerciseWithParticipations.getBuildAndTestStudentSubmissionsAfterDueDate())
                .as("buildAndTestStudentSubmissionsAfterDueDate should be auto-computed and persisted").isNotNull().isAfter(exerciseToBeImported.getDueDate());
        assertThat(importedExerciseWithParticipations.getAllowFeedbackRequests()).as("feedback requests must be disabled when run tests after due date is auto-computed").isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testImportProgrammingExercise_withOversizedInheritedBuildPlanConfiguration_shouldReturnBadRequest() throws Exception {
        // The source exercise carries an oversized build plan configuration, as could exist for data created before the size limit
        // was introduced. It is written directly to the entity to bypass the create/update validation.
        programmingExercise = programmingExerciseRepository.findWithPlagiarismDetectionConfigTeamConfigBuildConfigAndGradingCriteriaById(programmingExercise.getId()).orElseThrow();
        var oversizedPhase = new BuildPhaseDTO("Test", "a".repeat(MAX_BUILD_PLAN_CONFIGURATION_LENGTH + 1), BuildPhaseCondition.ALWAYS, false, List.of());
        programmingExercise.getBuildConfig().setBuildPlanConfiguration(new BuildPlanPhasesDTO(List.of(oversizedPhase), "ubuntu:latest").toBuildPlanConfiguration());
        programmingExerciseBuildConfigRepository.save(programmingExercise.getBuildConfig());
        programmingExercise = programmingExerciseRepository.findWithPlagiarismDetectionConfigTeamConfigBuildConfigAndGradingCriteriaById(programmingExercise.getId()).orElseThrow();

        ProgrammingExercise exerciseToBeImported = ProgrammingExerciseFactory.generateToBeImportedProgrammingExercise("ImportOversizedTitle", "importoversized",
                programmingExercise, courseUtilService.addEnrolledEmptyCourse(TEST_PREFIX));
        // The import request omits the build plan configuration, so it is inherited from the oversized source exercise and must be
        // rejected by the size validation after inheritance, not silently persisted again.
        exerciseToBeImported.getBuildConfig().setBuildPlanConfiguration(null);
        exerciseToBeImported.setChannelName("testchannel-pe-importoversized");
        exerciseToBeImported.setCompetencyLinks(Set.of(new CompetencyExerciseLink(competency, exerciseToBeImported, 1)));
        exerciseToBeImported.getCompetencyLinks().forEach(link -> link.getCompetency().setCourse(null));

        request.postAndExpectError("/api/programming/programming-exercises/import?sourceExerciseId=" + programmingExercise.getId() + "&recreateBuildPlans=false",
                exerciseToBeImported, HttpStatus.BAD_REQUEST, "buildPlanConfigurationTooLong");
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
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
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
    void createProgrammingExercise_invalidPlagiarismDetectionConfig_badRequest() throws Exception {
        programmingExerciseTestService.createProgrammingExercise_invalidPlagiarismDetectionConfig_badRequest();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createProgrammingExerciseForExam_withoutBuildPlanConfiguration_setsAfterDueDateForResultPhases() throws Exception {
        programmingExerciseTestService.createProgrammingExerciseForExam_withoutBuildPlanConfiguration_setsAfterDueDateForResultPhases();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateProgrammingExercise_invalidPlagiarismDetectionConfig_badRequest() throws Exception {
        programmingExerciseTestService.updateProgrammingExercise_invalidPlagiarismDetectionConfig_badRequest();
    }

    /**
     * Ensures <a href="https://github.com/ls1intum/Artemis/issues/7188">issue #7188</a> does not occur again
     *
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importFromFile_validImportZip_changeTitle_success() throws Exception {

        String uniqueSuffix = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase();
        String newTitle = "TITLE" + uniqueSuffix;
        String newShortName = "SHORT" + uniqueSuffix;

        ImportFileResult importResult = programmingExerciseImportTestService.prepareExerciseImport("test-data/import-from-file/valid-import.zip", exercise -> {
            String oldTitle = exercise.getTitle();
            exercise.setTitle(newTitle);
            exercise.setShortName(newShortName);
            return oldTitle;
        }, course);

        ProgrammingExercise importedExercise = importResult.importedExercise();
        String oldTitle = (String) importResult.additionalData();

        assertThat(importedExercise).isNotNull();
        assertThat(importedExercise.getTitle()).isEqualTo(newTitle);
        assertThat(importedExercise.getProgrammingLanguage()).isEqualTo(importResult.parsedExercise().getProgrammingLanguage());
        assertThat(importedExercise.getCourseViaExerciseGroupOrCourseMember()).isEqualTo(course);

        String projectKey = importResult.parsedExercise().getProjectKey();
        Path exercisePath = Path.of(repoClonePath, projectKey);
        int newTitleCount = programmingExerciseImportTestService.countOccurrencesInDirectory(exercisePath, newTitle);
        int oldTitleCount = programmingExerciseImportTestService.countOccurrencesInDirectory(exercisePath, oldTitle);

        assertThat(newTitleCount).isEqualTo(programmingExerciseImportTestService.countOccurrencesInZip(importResult.resource(), oldTitle));
        assertThat(oldTitleCount).isZero();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importFromFile_validImportZip() throws Exception {

        ImportFileResult importResult = programmingExerciseImportTestService.prepareExerciseImport("test-data/import-from-file/valid-import.zip", exercise -> null, course);
        ProgrammingExercise importedExercise = importResult.importedExercise();

        assertThat(importedExercise).isNotNull();
        assertThat(importedExercise.getTitle()).isEqualTo(importResult.parsedExercise().getTitle());
        assertThat(importedExercise.getProgrammingLanguage()).isEqualTo(importResult.parsedExercise().getProgrammingLanguage());
        assertThat(importedExercise.getCourseViaExerciseGroupOrCourseMember()).isEqualTo(course);
    }

    /**
     * Ensures <a href="https://github.com/ls1intum/Artemis/issues/8562">issue #8562</a> does not occur again
     *
     * This test verifies that build plans are triggered during exercise import from file.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importFromFile_verifyBuildPlansTriggered() throws Exception {

        ImportFileResult importResult = programmingExerciseImportTestService.prepareExerciseImport("test-data/import-from-file/valid-import.zip", exercise -> null, course);

        // Get participations from the imported exercise
        TemplateProgrammingExerciseParticipation templateParticipation = templateProgrammingExerciseParticipationRepository
                .findByProgrammingExerciseId(importResult.importedExercise().getId()).orElseThrow();
        SolutionProgrammingExerciseParticipation solutionParticipation = solutionProgrammingExerciseParticipationRepository
                .findByProgrammingExerciseId(importResult.importedExercise().getId()).orElseThrow();

        verify(localCITriggerService, timeout(5000).times(1)).triggerBuild(eq(templateParticipation));
        verify(localCITriggerService, timeout(5000).times(1)).triggerBuild(eq(solutionParticipation));
    }

    /**
     * Mocks the Docker calls the two builds that creating an exercise triggers - one for the template and one for the solution repository - would otherwise make.
     */
    private void mockDockerForTheBuildsCreatingAnExerciseTriggers() throws Exception {
        // Mock dockerClient.copyArchiveFromContainerCmd() such that it returns a dummy commitHash for both the assignment and the test repository.
        dockerClientTestService.mockInputStreamReturnedFromContainer(dockerClient, LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY + "/testing-dir/assignment/.git/refs/heads/[^/]+",
                Map.of("assignmentCommitHash", DUMMY_COMMIT_HASH), Map.of("assignmentCommitHash", DUMMY_COMMIT_HASH));
        dockerClientTestService.mockInputStreamReturnedFromContainer(dockerClient, LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY + "/testing-dir/.git/refs/heads/[^/]+",
                Map.of("testsCommitHash", DUMMY_COMMIT_HASH), Map.of("testsCommitHash", DUMMY_COMMIT_HASH));

        dockerClientTestService.mockInspectImage(dockerClient);

        // Mock dockerClient.copyArchiveFromContainerCmd() such that it returns the XMLs containing the test results.
        Map<String, String> templateBuildTestResults = dockerClientTestService.createMapFromTestResultsFolder(ALL_FAIL_TEST_RESULTS_PATH);
        Map<String, String> solutionBuildTestResults = dockerClientTestService.createMapFromTestResultsFolder(ALL_SUCCEED_TEST_RESULTS_PATH);
        dockerClientTestService.mockInputStreamReturnedFromContainer(dockerClient, LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY + LOCAL_CI_RESULTS_DIRECTORY,
                templateBuildTestResults, solutionBuildTestResults);
    }

    /**
     * Creates a programming exercise through the setup endpoint, the way an instructor does, and returns it with the repositories the server filled in place.
     */
    private ProgrammingExercise createExerciseThroughTheSetupEndpoint(ProgrammingExercise newExercise, String channelName) throws Exception {
        return createExerciseThroughTheSetupEndpoint(newExercise, channelName, false);
    }

    private ProgrammingExercise createExerciseThroughTheSetupEndpoint(ProgrammingExercise newExercise, String channelName, boolean emptyRepositories) throws Exception {
        mockDockerForTheBuildsCreatingAnExerciseTriggers();
        newExercise.setChannelName(channelName);
        var params = new LinkedMultiValueMap<String, String>();
        params.add("emptyRepositories", String.valueOf(emptyRepositories));
        ProgrammingExercise createdExercise = request.postWithResponseBody("/api/programming/programming-exercises/setup", newExercise, ProgrammingExercise.class, params,
                HttpStatus.CREATED);
        createdExercisesToCleanUp.add(createdExercise);
        return createdExercise;
    }

    /** Lists what a repository of the given exercise holds, read from the bare repository the server pushed to. */
    private List<String> filesOf(ProgrammingExercise exercise, RepositoryType repositoryType) {
        return localVCRepositoryTestService.listFilePaths(localVCRepositoryTestService.repositoryUri(exercise.getProjectKey(), exercise.generateRepositoryName(repositoryType)));
    }

    private String readFileOf(ProgrammingExercise exercise, RepositoryType repositoryType, String filePath) {
        return localVCRepositoryTestService.readFile(localVCRepositoryTestService.repositoryUri(exercise.getProjectKey(), exercise.generateRepositoryName(repositoryType)),
                filePath);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateProgrammingExerciseWithSequentialTestRuns() throws Exception {
        ProgrammingExercise newExercise = ProgrammingExerciseFactory.generateProgrammingExercise(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(7), course);
        newExercise.setProjectType(ProjectType.PLAIN_GRADLE);
        // Enable sequential test runs
        newExercise.getBuildConfig().setSequentialTestRuns(true);

        ProgrammingExercise createdExercise = createExerciseThroughTheSetupEndpoint(newExercise, "testchannelname-pe-sequential");

        assertThat(createdExercise.getBuildConfig().hasSequentialTestRuns()).as("the exercise keeps the sequential test runs it was created with").isTrue();
        List<String> testFiles = filesOf(createdExercise, RepositoryType.TESTS);
        // Sequential test runs split the test repository into two build stages that are run one after the other.
        assertThat(testFiles).as("the structural build stage is set up").anyMatch(path -> path.startsWith("structural/"));
        assertThat(testFiles).as("the behavior build stage is set up").anyMatch(path -> path.startsWith("behavior/"));
        assertThat(testFiles).as("a gradle exercise gets a build.gradle and no pom.xml").contains("build.gradle").noneMatch(path -> path.endsWith(POM_XML));
        assertThat(testFiles).as("no template placeholder survives into the repository").noneMatch(path -> path.contains("${"));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateMavenProgrammingExerciseWithSequentialTestRunsWritesAStagePomPerBuildStage() throws Exception {
        // Only Maven exercises need a project file per build stage, because each stage is a Maven module of its own. Gradle drives both stages from the root project.
        ProgrammingExercise newExercise = ProgrammingExerciseFactory.generateProgrammingExercise(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(7), course);
        newExercise.setProjectType(ProjectType.PLAIN_MAVEN);
        newExercise.getBuildConfig().setSequentialTestRuns(true);

        ProgrammingExercise createdExercise = createExerciseThroughTheSetupEndpoint(newExercise, "testchannel-pe-seq-maven");

        List<String> testFiles = filesOf(createdExercise, RepositoryType.TESTS);
        assertThat(testFiles).as("each build stage gets its own pom.xml").contains("structural/" + POM_XML, "behavior/" + POM_XML);
        assertThat(testFiles).as("the tests of both build stages are placed in the package directory").anyMatch(path -> path.startsWith("structural/test/de/test/"))
                .anyMatch(path -> path.startsWith("behavior/test/de/test/"));
        // A sequential exercise aggregates its build stages, so the root project has to be packaged as a POM rather than as a JAR.
        assertThat(localVCRepositoryTestService.readFile(new LocalVCRepositoryUri(createdExercise.getTestRepositoryUri()), POM_XML)).as("the root pom aggregates the build stages")
                .contains("<packaging>pom</packaging>").doesNotContain("${packaging}");
    }

    /**
     * Exercise creation for an AI-generated exercise asks for empty repositories: the template files are set up as
     * usual, so that the build still works, and only the sources are cleared afterwards, so that the generator has
     * somewhere to write to instead of having to remove the template solution first.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateProgrammingExerciseWithEmptyRepositoriesClearsOnlyTheSources() throws Exception {
        ProgrammingExercise newExercise = ProgrammingExerciseFactory.generateProgrammingExercise(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(7), course);
        newExercise.setProjectType(ProjectType.PLAIN_GRADLE);

        ProgrammingExercise createdExercise = createExerciseThroughTheSetupEndpoint(newExercise, "testchannel-pe-empty", true);

        for (RepositoryType repositoryType : List.of(RepositoryType.TEMPLATE, RepositoryType.SOLUTION)) {
            List<String> files = filesOf(createdExercise, repositoryType);
            assertThat(files).as("the sources of the %s repository are cleared and only the placeholder that keeps the directory remains", repositoryType.getName())
                    .filteredOn(path -> path.startsWith("src/")).containsExactly("src/.gitkeep");
            assertThat(files).as("the build scaffolding is kept, so the exercise still builds").contains("build.gradle");
        }
        List<String> testFiles = filesOf(createdExercise, RepositoryType.TESTS);
        assertThat(testFiles).as("the tests are cleared as well").filteredOn(path -> path.startsWith("test/")).containsExactly("test/.gitkeep");
        assertThat(testFiles).as("the test project file is kept").contains("build.gradle");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateJavaProgrammingExerciseWithStaticCodeAnalysisWritesTheAnalyzerConfiguration() throws Exception {
        // Java keeps the analyzer configuration inside its regular test templates.
        ProgrammingExercise newExercise = ProgrammingExerciseFactory.generateProgrammingExercise(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(7), course);
        newExercise.setProjectType(ProjectType.PLAIN_GRADLE);
        newExercise.setStaticCodeAnalysisEnabled(true);

        ProgrammingExercise createdExercise = createExerciseThroughTheSetupEndpoint(newExercise, "testchannel-pe-sca-java");

        assertThat(createdExercise.isStaticCodeAnalysisEnabled()).as("the exercise keeps the static code analysis it was created with").isTrue();
        // Without the configuration files the analyzers the build script invokes have nothing to run against, so the exercise would build but report no issues at all.
        assertThat(filesOf(createdExercise, RepositoryType.TESTS)).as("the configuration of every analyzer is part of the test repository").contains(
                "staticCodeAnalysisConfig/checkstyle-configuration.xml", "staticCodeAnalysisConfig/pmd-configuration.xml", "staticCodeAnalysisConfig/spotbugs-exclusions.xml");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreatePythonProgrammingExerciseWithStaticCodeAnalysisCopiesTheSeparateTemplateFiles() throws Exception {
        // Python, unlike Java, keeps its analyzer configuration in a staticCodeAnalysis directory of its own, which is only copied when the exercise asks for it.
        ProgrammingExercise newExercise = ProgrammingExerciseFactory.generateProgrammingExercise(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(7), course,
                ProgrammingLanguage.PYTHON);
        newExercise.setProjectType(null);
        newExercise.setStaticCodeAnalysisEnabled(true);

        ProgrammingExercise createdExercise = createExerciseThroughTheSetupEndpoint(newExercise, "testchannel-pe-sca-python");

        assertThat(filesOf(createdExercise, RepositoryType.TESTS)).as("the separate analyzer configuration is copied into the test repository").contains("ruff-student.toml");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteProgrammingExerciseRemovesItsAuxiliaryRepositoriesFromDisk() throws Exception {
        ProgrammingExercise newExercise = ProgrammingExerciseFactory.generateProgrammingExercise(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(7), course);
        newExercise.setProjectType(ProjectType.PLAIN_GRADLE);
        AuxiliaryRepository auxiliaryRepository = new AuxiliaryRepository();
        auxiliaryRepository.setName("solutionhints");
        auxiliaryRepository.setCheckoutDirectory("hints");
        auxiliaryRepository.setDescription("hints for the students");
        newExercise.setAuxiliaryRepositories(new ArrayList<>(List.of(auxiliaryRepository)));

        ProgrammingExercise createdExercise = createExerciseThroughTheSetupEndpoint(newExercise, "testchannel-pe-aux");

        Path auxiliaryRepositoryPath = localVCRepositoryTestService.repositoryUri(createdExercise.getProjectKey(), createdExercise.generateRepositoryName("solutionhints"))
                .getLocalRepositoryPath(localVCBasePath);
        Path testsRepositoryPath = localVCRepositoryTestService.repositoryUri(createdExercise.getProjectKey(), createdExercise.generateRepositoryName(RepositoryType.TESTS))
                .getLocalRepositoryPath(localVCBasePath);
        assertThat(auxiliaryRepositoryPath).as("the auxiliary repository is created along with the exercise").isDirectory();

        var params = new LinkedMultiValueMap<String, String>();
        params.add("deleteStudentReposBuildPlans", "true");
        params.add("deleteBaseReposBuildPlans", "true");
        request.delete("/api/programming/programming-exercises/" + createdExercise.getId(), HttpStatus.OK, params);

        // A repository left behind on disk blocks creating a new exercise with the same short name, which is why deletion has to reach the auxiliary repositories as well.
        assertThat(auxiliaryRepositoryPath).as("the auxiliary repository is removed along with the exercise").doesNotExist();
        assertThat(testsRepositoryPath).as("the base repositories are removed as well").doesNotExist();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateSwiftProgrammingExerciseReplacesThePackageNameInPathsAndContent() throws Exception {
        // Swift is the one language whose templates carry the package name in directory and file names, so creating the exercise has to rename them.
        ProgrammingExercise newExercise = ProgrammingExerciseFactory.generateProgrammingExercise(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(7), course,
                ProgrammingLanguage.SWIFT);
        newExercise.setProjectType(ProjectType.PLAIN);

        ProgrammingExercise createdExercise = createExerciseThroughTheSetupEndpoint(newExercise, "testchannelname-pe-swift");

        assertThat(createdExercise.getPackageName()).as("the exercise keeps the package name it was created with").isEqualTo("testPackage");
        for (RepositoryType repositoryType : List.of(RepositoryType.TEMPLATE, RepositoryType.SOLUTION)) {
            List<String> files = filesOf(createdExercise, repositoryType);
            assertThat(files).as("the sources are placed in a directory named after the package").anyMatch(path -> path.startsWith("Sources/testPackageLib/"));
            assertThat(files).as("no package name placeholder is left in a path").noneMatch(path -> path.contains(PACKAGE_NAME_FOLDER_PLACEHOLDER));
            assertThat(files).as("the Swift package manifest is part of the repository").contains("Package.swift");
        }
        assertThat(readFileOf(createdExercise, RepositoryType.TEMPLATE, "Package.swift")).as("the manifest names the package instead of the placeholder").contains("testPackage")
                .doesNotContain(PACKAGE_NAME_PLACEHOLDER);
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
