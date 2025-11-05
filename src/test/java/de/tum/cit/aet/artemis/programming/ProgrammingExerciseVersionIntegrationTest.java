package de.tum.cit.aet.artemis.programming;

import static de.tum.cit.aet.artemis.exercise.util.ExerciseVersionUtilService.zonedDateTimeBiPredicate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import com.fasterxml.jackson.core.type.TypeReference;

import de.tum.cit.aet.artemis.assessment.domain.Visibility;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseVersion;
import de.tum.cit.aet.artemis.exercise.dto.versioning.ProgrammingExerciseSnapshotDTO;
import de.tum.cit.aet.artemis.exercise.service.ExerciseVersionService;
import de.tum.cit.aet.artemis.exercise.util.ExerciseVersionUtilService;
import de.tum.cit.aet.artemis.programming.domain.AuxiliaryRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTestCase;
import de.tum.cit.aet.artemis.programming.domain.submissionpolicy.LockRepositoryPolicy;
import de.tum.cit.aet.artemis.programming.dto.ProgrammingExerciseResetOptionsDTO;
import de.tum.cit.aet.artemis.programming.dto.ProgrammingExerciseTestCaseDTO;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestCaseTestRepository;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseFactory;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseImportTestService;

/**
 * Integration tests for exercise versioning on ProgrammingExercise operations.
 */
class ProgrammingExerciseVersionIntegrationTest extends AbstractProgrammingIntegrationLocalCILocalVCTestBase {

    private static final String TEST_PREFIX = "progexversion";

    @Autowired
    private ExerciseVersionUtilService exerciseVersionUtilService;

    @Autowired
    private ProgrammingExerciseImportTestService programmingExerciseImportTestService;

    @Autowired
    private ExerciseVersionService exerciseVersionService;

    @Autowired
    private ProgrammingExerciseTestCaseTestRepository programmingExerciseTestCaseRepository;

    private Course course;

    private ProgrammingExercise programmingExercise;

    @Override
    protected String getTestPrefix() {
        return TEST_PREFIX;
    }

    @BeforeEach
    void initTestCase() throws Exception {
        userUtilService.addUsers(TEST_PREFIX, 2, 1, 1, 1);
        course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        // Arrange: Create a new programming exercise
        ProgrammingExercise newExercise = ProgrammingExerciseFactory.generateProgrammingExercise(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(7), course);

        AuxiliaryRepository auxiliaryRepository = new AuxiliaryRepository();
        auxiliaryRepository.setName("extra");
        auxiliaryRepository.setCheckoutDirectory("extra");
        newExercise.setAuxiliaryRepositories(new ArrayList<>(List.of(auxiliaryRepository)));
        // Act: Create the exercise via setup endpoint
        this.programmingExercise = request.postWithResponseBody("/api/programming/programming-exercises/setup", newExercise, ProgrammingExercise.class, HttpStatus.CREATED);

        // Assert: Verify operation succeeded
        assertThat(programmingExercise).isNotNull();
        assertThat(programmingExercise.getId()).isNotNull();
        // wait for solution/template/test to build and generate git commits
        await().untilAsserted(() -> {
            ExerciseVersion exerciseVersion = exerciseVersionUtilService.verifyExerciseVersionCreated(programmingExercise.getId(), TEST_PREFIX + "instructor1",
                    ExerciseType.PROGRAMMING);
            assertThat(exerciseVersion.getExerciseSnapshot().programmingData().solutionParticipation().commitId()).isNotNull();
            assertThat(exerciseVersion.getExerciseSnapshot().programmingData().templateParticipation().commitId()).isNotNull();
            assertThat(exerciseVersion.getExerciseSnapshot().programmingData().testsCommitId()).isNotNull();
        });
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateProgrammingExercise_createsExerciseVersion() throws Exception {
        // Assert: Verify exercise version was created
        exerciseVersionUtilService.verifyExerciseVersionCreated(programmingExercise.getId(), TEST_PREFIX + "instructor1", ExerciseType.PROGRAMMING);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGenerateTests_createsExerciseVersion() throws Exception {

        ExerciseVersion originalVersion = exerciseVersionUtilService.verifyExerciseVersionCreated(programmingExercise.getId(), TEST_PREFIX + "instructor1",
                ExerciseType.PROGRAMMING);
        ProgrammingExerciseSnapshotDTO originalSnapshot = originalVersion.getExerciseSnapshot().programmingData();
        final var path = "/api/programming/programming-exercises/" + programmingExercise.getId() + "/generate-tests";
        request.putWithResponseBody(path, programmingExercise, String.class, HttpStatus.OK);

        await().untilAsserted(() -> {
            ExerciseVersion newExerciseVerion = exerciseVersionUtilService.verifyExerciseVersionCreated(programmingExercise.getId(), TEST_PREFIX + "instructor1",
                    ExerciseType.PROGRAMMING);
            assertThat(newExerciseVerion.getId()).isNotEqualTo(originalVersion.getId());
        });

        ExerciseVersion newVersion = exerciseVersionUtilService.verifyExerciseVersionCreated(programmingExercise.getId(), TEST_PREFIX + "instructor1", ExerciseType.PROGRAMMING);
        ProgrammingExerciseSnapshotDTO newSnapshot = newVersion.getExerciseSnapshot().programmingData();
        assertThat(newSnapshot).usingRecursiveComparison().withEqualsForType(zonedDateTimeBiPredicate, ZonedDateTime.class).isNotEqualTo(originalSnapshot);

    }

    @ParameterizedTest
    @CsvSource({ "true,false", "false,true", "true,true", "false,false" })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testReset_createsExerciseVersion(boolean deleteParticipationsSubmissionsAndResults, boolean recreateBuildPlans) throws Exception {

        ExerciseVersion originalVersion = exerciseVersionUtilService.verifyExerciseVersionCreated(programmingExercise.getId(), TEST_PREFIX + "instructor1",
                ExerciseType.PROGRAMMING);
        ProgrammingExerciseSnapshotDTO originalSnapshot = originalVersion.getExerciseSnapshot().programmingData();
        final var path = "/api/programming/programming-exercises/" + programmingExercise.getId() + "/reset";
        ProgrammingExerciseResetOptionsDTO resetOptions = new ProgrammingExerciseResetOptionsDTO(deleteParticipationsSubmissionsAndResults, recreateBuildPlans);
        request.putWithResponseBody(path, resetOptions, String.class, HttpStatus.OK);

        await().untilAsserted(() -> {
            ExerciseVersion newExerciseVerion = exerciseVersionUtilService.verifyExerciseVersionCreated(programmingExercise.getId(), TEST_PREFIX + "instructor1",
                    ExerciseType.PROGRAMMING);
            if (recreateBuildPlans) {
                assertThat(newExerciseVerion.getId()).isNotEqualTo(originalVersion.getId());
            }
            else {
                assertThat(newExerciseVerion.getId()).isEqualTo(originalVersion.getId());
            }
        });

        if (recreateBuildPlans) {

            ExerciseVersion newVersion = exerciseVersionUtilService.verifyExerciseVersionCreated(programmingExercise.getId(), TEST_PREFIX + "instructor1",
                    ExerciseType.PROGRAMMING);
            ProgrammingExerciseSnapshotDTO newSnapshot = newVersion.getExerciseSnapshot().programmingData();
            assertThat(newSnapshot).usingRecursiveComparison().withEqualsForType(zonedDateTimeBiPredicate, ZonedDateTime.class).isNotEqualTo(originalSnapshot);
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testImportExercise_createsExerciseVersion() throws Exception {

        ProgrammingExercise exerciseToBeImported = ProgrammingExerciseFactory.generateToBeImportedProgrammingExercise("ImportTitle", "imported", programmingExercise,
                courseUtilService.addEmptyCourse());

        // Create request parameters
        var params = new LinkedMultiValueMap<String, String>();
        params.add("recreateBuildPlans", String.valueOf(true));

        // Import the exercise and load all referenced entities
        var importedExercise = request.postWithResponseBody("/api/programming/programming-exercises/import/" + programmingExercise.getId(), exerciseToBeImported,
                ProgrammingExercise.class, params, HttpStatus.OK);

        exerciseVersionUtilService.verifyExerciseVersionCreated(importedExercise.getId(), TEST_PREFIX + "instructor1", ExerciseType.PROGRAMMING);

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testImportExerciseFromFile_createsExerciseVersion() throws Exception {

        String uniqueSuffix = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase();
        String newTitle = "TITLE" + uniqueSuffix;
        String newShortName = "SHORT" + uniqueSuffix;

        ProgrammingExerciseImportTestService.ImportFileResult importResult = programmingExerciseImportTestService
                .prepareExerciseImport("test-data/import-from-file/valid-import.zip", exercise -> {
                    String oldTitle = exercise.getTitle();
                    exercise.setTitle(newTitle);
                    exercise.setShortName(newShortName);
                    return oldTitle;
                }, course);

        ProgrammingExercise importedExercise = importResult.importedExercise();
        exerciseVersionUtilService.verifyExerciseVersionCreated(importedExercise.getId(), TEST_PREFIX + "instructor1", ExerciseType.PROGRAMMING);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateProgrammingExerciseTimeline_createsExerciseVersion() throws Exception {

        ExerciseVersion originalVersion = exerciseVersionUtilService.verifyExerciseVersionCreated(programmingExercise.getId(), TEST_PREFIX + "instructor1",
                ExerciseType.PROGRAMMING);

        ExerciseVersionUtilService.updateExercise(programmingExercise);
        final var endpoint = "/api/programming/programming-exercises/timeline";
        MultiValueMap<String, String> params = new HttpHeaders();
        params.add("notificationText", "The notification text");
        request.putWithResponseBodyAndParams(endpoint, programmingExercise, ProgrammingExercise.class, HttpStatus.OK, params);

        ExerciseVersion newVersion = exerciseVersionUtilService.verifyExerciseVersionCreated(programmingExercise.getId(), TEST_PREFIX + "instructor1", ExerciseType.PROGRAMMING);
        assertThat(newVersion.getId()).isNotEqualTo(originalVersion.getId());
        assertThat(newVersion.getExerciseSnapshot()).usingRecursiveComparison().withEqualsForType(zonedDateTimeBiPredicate, ZonedDateTime.class)
                .isNotEqualTo(originalVersion.getExerciseSnapshot());

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateProgrammingExerciseProblemStatement_createsExerciseVersion() throws Exception {

        ExerciseVersion originalVersion = exerciseVersionUtilService.verifyExerciseVersionCreated(programmingExercise.getId(), TEST_PREFIX + "instructor1",
                ExerciseType.PROGRAMMING);

        final String newStatement = "This is a new problem statement";

        final var endpoint = "/api/programming/programming-exercises/" + programmingExercise.getId() + "/problem-statement";
        request.patchWithResponseBody(endpoint, newStatement, ProgrammingExercise.class, HttpStatus.OK, MediaType.TEXT_PLAIN);

        ExerciseVersion newVersion = exerciseVersionUtilService.verifyExerciseVersionCreated(programmingExercise.getId(), TEST_PREFIX + "instructor1", ExerciseType.PROGRAMMING);
        assertThat(newVersion.getId()).isNotEqualTo(originalVersion.getId());
        assertThat(newVersion.getExerciseSnapshot()).usingRecursiveComparison().withEqualsForType(zonedDateTimeBiPredicate, ZonedDateTime.class)
                .isNotEqualTo(originalVersion.getExerciseSnapshot());
        assertThat(newVersion.getExerciseSnapshot().problemStatement()).isNotEqualTo(originalVersion.getExerciseSnapshot().problemStatement());
        assertThat(newVersion.getExerciseSnapshot().problemStatement()).isEqualTo(newStatement);

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateProgrammingExercise_createsExerciseVersion() throws Exception {

        ExerciseVersion originalVersion = exerciseVersionUtilService.verifyExerciseVersionCreated(programmingExercise.getId(), TEST_PREFIX + "instructor1",
                ExerciseType.PROGRAMMING);

        ExerciseVersionUtilService.updateExercise(programmingExercise);

        request.putWithResponseBody("/api/programming/programming-exercises", programmingExercise, ProgrammingExercise.class, HttpStatus.OK);

        ExerciseVersion newVersion = exerciseVersionUtilService.verifyExerciseVersionCreated(programmingExercise.getId(), TEST_PREFIX + "instructor1", ExerciseType.PROGRAMMING);
        assertThat(newVersion.getId()).isNotEqualTo(originalVersion.getId());
        assertThat(newVersion.getExerciseSnapshot()).usingRecursiveComparison().withEqualsForType(zonedDateTimeBiPredicate, ZonedDateTime.class)
                .isNotEqualTo(originalVersion.getExerciseSnapshot());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testReEvaluateAndUpdateProgrammingExercise_createsExerciseVersion() throws Exception {

        ExerciseVersion originalVersion = exerciseVersionUtilService.verifyExerciseVersionCreated(programmingExercise.getId(), TEST_PREFIX + "instructor1",
                ExerciseType.PROGRAMMING);

        ExerciseVersionUtilService.updateExercise(programmingExercise);
        final var endpoint = "/api/programming/programming-exercises/" + programmingExercise.getId() + "/re-evaluate?deleteFeedback=false";
        request.putWithResponseBody(endpoint, programmingExercise, ProgrammingExercise.class, HttpStatus.OK);

        ExerciseVersion newVersion = exerciseVersionUtilService.verifyExerciseVersionCreated(programmingExercise.getId(), TEST_PREFIX + "instructor1", ExerciseType.PROGRAMMING);
        assertThat(newVersion.getId()).isNotEqualTo(originalVersion.getId());
        assertThat(newVersion.getExerciseSnapshot()).usingRecursiveComparison().withEqualsForType(zonedDateTimeBiPredicate, ZonedDateTime.class)
                .isNotEqualTo(originalVersion.getExerciseSnapshot());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testAddSubmissionPolicyProgrammingExercise_createsExerciseVersion() throws Exception {

        ExerciseVersion originalVersion = exerciseVersionUtilService.verifyExerciseVersionCreated(programmingExercise.getId(), TEST_PREFIX + "instructor1",
                ExerciseType.PROGRAMMING);

        final var endpoint = "/api/programming/programming-exercises/" + programmingExercise.getId() + "/submission-policy";

        var policy = new LockRepositoryPolicy();
        policy.setActive(true);
        policy.setSubmissionLimit(5);

        request.post(endpoint, policy, HttpStatus.CREATED);

        ExerciseVersion newVersion = exerciseVersionUtilService.verifyExerciseVersionCreated(programmingExercise.getId(), TEST_PREFIX + "instructor1", ExerciseType.PROGRAMMING);
        assertThat(newVersion.getId()).isNotEqualTo(originalVersion.getId());
        assertThat(newVersion.getExerciseSnapshot()).usingRecursiveComparison().withEqualsForType(zonedDateTimeBiPredicate, ZonedDateTime.class)
                .isNotEqualTo(originalVersion.getExerciseSnapshot());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testResetTestCases_createsExerciseVersion() throws Exception {

        ExerciseVersion originalVersion = exerciseVersionUtilService.verifyExerciseVersionCreated(programmingExercise.getId(), TEST_PREFIX + "instructor1",
                ExerciseType.PROGRAMMING);
        // set up test cases
        programmingExerciseUtilService.addTestCasesToProgrammingExercise(programmingExercise);
        programmingExerciseRepository.save(programmingExercise);
        exerciseVersionService.createExerciseVersion(programmingExercise);

        final var endpoint = "/api/programming/programming-exercises/" + programmingExercise.getId() + "/test-cases/reset";
        request.patchWithResponseBody(endpoint, "{}", new TypeReference<List<ProgrammingExerciseTestCase>>() {
        }, HttpStatus.OK);

        ExerciseVersion newVersion = exerciseVersionUtilService.verifyExerciseVersionCreated(programmingExercise.getId(), TEST_PREFIX + "instructor1", ExerciseType.PROGRAMMING);
        assertThat(newVersion.getId()).isNotEqualTo(originalVersion.getId());
        assertThat(newVersion.getExerciseSnapshot()).usingRecursiveComparison().withEqualsForType(zonedDateTimeBiPredicate, ZonedDateTime.class)
                .isNotEqualTo(originalVersion.getExerciseSnapshot());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateTestCases_createsExerciseVersion() throws Exception {

        ExerciseVersion originalVersion = exerciseVersionUtilService.verifyExerciseVersionCreated(programmingExercise.getId(), TEST_PREFIX + "instructor1",
                ExerciseType.PROGRAMMING);
        // set up test cases
        programmingExerciseUtilService.addTestCasesToProgrammingExercise(programmingExercise);
        programmingExerciseRepository.save(programmingExercise);
        exerciseVersionService.createExerciseVersion(programmingExercise);

        final var testCases = programmingExerciseTestCaseRepository.findByExerciseId(programmingExercise.getId());
        final var updates = testCases.stream().map(testCase -> new ProgrammingExerciseTestCaseDTO(testCase.getId(), testCase.getId() + 42.0, testCase.getId() + 1.0,
                testCase.getId() + 2.0, Visibility.AFTER_DUE_DATE)).toList();
        final var endpoint = "/programming/programming-exercises/" + programmingExercise.getId() + "/update-test-cases";
        request.patchWithResponseBody("/api" + endpoint, updates, new TypeReference<List<ProgrammingExerciseTestCase>>() {
        }, HttpStatus.OK);

        ExerciseVersion newVersion = exerciseVersionUtilService.verifyExerciseVersionCreated(programmingExercise.getId(), TEST_PREFIX + "instructor1", ExerciseType.PROGRAMMING);
        assertThat(newVersion.getId()).isNotEqualTo(originalVersion.getId());
        assertThat(newVersion.getExerciseSnapshot()).usingRecursiveComparison().withEqualsForType(zonedDateTimeBiPredicate, ZonedDateTime.class)
                .isNotEqualTo(originalVersion.getExerciseSnapshot());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testVersionCreationOnProcessNewPush_templateRepository() throws Exception {

        ExerciseVersion originalVersion = exerciseVersionUtilService.verifyExerciseVersionCreated(programmingExercise.getId(), TEST_PREFIX + "instructor1",
                ExerciseType.PROGRAMMING);

        Long templateParticipationId = programmingExercise.getTemplateParticipation().getId();

        request.postWithoutLocation("/api/programming/repository/" + templateParticipationId + "/file?file=Template.java", null, HttpStatus.OK, null);
        request.postWithoutLocation("/api/programming/repository/" + templateParticipationId + "/commit", null, HttpStatus.OK, null);

        await().untilAsserted(() -> {
            ExerciseVersion newVersion = exerciseVersionUtilService.verifyExerciseVersionCreated(programmingExercise.getId(), TEST_PREFIX + "instructor1",
                    ExerciseType.PROGRAMMING);
            assertThat(originalVersion.getId()).isNotEqualTo(newVersion.getId());
            assertThat(newVersion.getExerciseSnapshot()).isNotNull();
            assertThat(newVersion.getExerciseSnapshot().programmingData()).isNotNull();
            assertThat(originalVersion.getExerciseSnapshot().programmingData().templateParticipation()).usingRecursiveComparison()
                    .isNotEqualTo(newVersion.getExerciseSnapshot().programmingData().templateParticipation());
        });
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testVersionCreationOnProcessNewPush_solutionRepository() throws Exception {

        ExerciseVersion originalVersion = exerciseVersionUtilService.verifyExerciseVersionCreated(programmingExercise.getId(), TEST_PREFIX + "instructor1",
                ExerciseType.PROGRAMMING);

        Long solutionParticipationId = programmingExercise.getSolutionParticipation().getId();

        request.postWithoutLocation("/api/programming/repository/" + solutionParticipationId + "/file?file=Template.java", null, HttpStatus.OK, null);
        request.postWithoutLocation("/api/programming/repository/" + solutionParticipationId + "/commit", null, HttpStatus.OK, null);

        await().untilAsserted(() -> {
            ExerciseVersion newVersion = exerciseVersionUtilService.verifyExerciseVersionCreated(programmingExercise.getId(), TEST_PREFIX + "instructor1",
                    ExerciseType.PROGRAMMING);
            assertThat(originalVersion.getId()).isNotEqualTo(newVersion.getId());
            assertThat(newVersion.getExerciseSnapshot()).isNotNull();
            assertThat(newVersion.getExerciseSnapshot().programmingData()).isNotNull();
            assertThat(originalVersion.getExerciseSnapshot().programmingData().solutionParticipation()).usingRecursiveComparison()
                    .isNotEqualTo(newVersion.getExerciseSnapshot().programmingData().solutionParticipation());
        });
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testVersionCreationOnProcessNewPush_auxiliaryRepository() throws Exception {

        ExerciseVersion originalVersion = exerciseVersionUtilService.verifyExerciseVersionCreated(programmingExercise.getId(), TEST_PREFIX + "instructor1",
                ExerciseType.PROGRAMMING);

        List<AuxiliaryRepository> auxiliaryRepositories = programmingExercise.getAuxiliaryRepositories();
        assertThat(auxiliaryRepositories).isNotEmpty();
        Long auxiliaryRepositoryId = auxiliaryRepositories.getFirst().getId();
        assertThat(auxiliaryRepositoryId).isNotNull();

        request.postWithoutLocation("/api/programming/auxiliary-repository/" + auxiliaryRepositoryId + "/file?file=Template.java", null, HttpStatus.OK, null);
        request.postWithoutLocation("/api/programming/auxiliary-repository/" + auxiliaryRepositoryId + "/commit", null, HttpStatus.OK, null);

        await().untilAsserted(() -> {
            ExerciseVersion newVersion = exerciseVersionUtilService.verifyExerciseVersionCreated(programmingExercise.getId(), TEST_PREFIX + "instructor1",
                    ExerciseType.PROGRAMMING);
            assertThat(originalVersion.getId()).isNotEqualTo(newVersion.getId());
            assertThat(newVersion.getExerciseSnapshot()).isNotNull();
            assertThat(newVersion.getExerciseSnapshot().programmingData()).isNotNull();
            assertThat(originalVersion.getExerciseSnapshot().programmingData().auxiliaryRepositories()).usingRecursiveComparison()
                    .isNotEqualTo(newVersion.getExerciseSnapshot().programmingData().auxiliaryRepositories());
        });
    }

}
